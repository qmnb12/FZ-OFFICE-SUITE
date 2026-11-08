package com.fz.office.writer;

import android.util.Xml;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Small offline DOCX bridge for the first Writer milestone.
 *
 * It deliberately handles a documented subset of WordprocessingML instead of
 * pretending to preserve features it does not understand. Existing package
 * parts are retained, while word/document.xml is rebuilt from the editable HTML.
 */
public final class DocxCodec {
    private static final String WORD_NS =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final int MAX_ENTRY_BYTES = 20 * 1024 * 1024;
    private static final int MAX_PACKAGE_BYTES = 80 * 1024 * 1024;

    private DocxCodec() { }

    public static final class LoadedDocument {
        public final String html;
        public final LinkedHashMap<String, byte[]> packageEntries;

        LoadedDocument(String html, LinkedHashMap<String, byte[]> packageEntries) {
            this.html = html;
            this.packageEntries = packageEntries;
        }
    }

    public static LoadedDocument readDocx(InputStream input) throws Exception {
        LinkedHashMap<String, byte[]> entries = readPackage(input);
        byte[] documentXml = entries.get("word/document.xml");
        if (documentXml == null) {
            throw new IOException("This file is not a supported DOCX document.");
        }
        String html = parseDocumentXml(documentXml);
        if (html.trim().isEmpty()) {
            html = "<p></p>";
        }
        return new LoadedDocument(html, entries);
    }

    public static LinkedHashMap<String, byte[]> readPackage(InputStream input) throws IOException {
        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
        ZipInputStream zip = new ZipInputStream(input);
        int totalBytes = 0;
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
                byte[] data = readCurrentEntry(zip, MAX_ENTRY_BYTES);
                totalBytes += data.length;
                if (totalBytes > MAX_PACKAGE_BYTES) {
                    throw new IOException("The DOCX package is too large to open safely.");
                }
                entries.put(entry.getName(), data);
            }
            zip.closeEntry();
        }
        zip.close();
        return entries;
    }

    private static byte[] readCurrentEntry(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        int total = 0;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > limit) {
                throw new IOException("A DOCX component is larger than the supported limit.");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static String parseDocumentXml(byte[] xml) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(new ByteArrayInputStream(xml), "UTF-8");
        StringBuilder html = new StringBuilder();
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && "body".equals(parser.getName())) {
                parseBody(parser, html);
                break;
            }
        }
        return html.toString();
    }

    private static void parseBody(XmlPullParser parser, StringBuilder html) throws Exception {
        while (true) {
            int event = parser.next();
            if (event == XmlPullParser.END_DOCUMENT) return;
            if (event == XmlPullParser.END_TAG && "body".equals(parser.getName())) return;
            if (event != XmlPullParser.START_TAG) continue;

            if ("p".equals(parser.getName())) {
                parseParagraph(parser, html);
            } else if ("tbl".equals(parser.getName())) {
                parseTable(parser, html);
            } else {
                skipElement(parser);
            }
        }
    }

    private static void parseParagraph(XmlPullParser parser, StringBuilder html) throws Exception {
        String alignment = "left";
        StringBuilder content = new StringBuilder();

        while (true) {
            int event = parser.next();
            if (event == XmlPullParser.END_DOCUMENT) break;
            if (event == XmlPullParser.END_TAG && "p".equals(parser.getName())) break;
            if (event != XmlPullParser.START_TAG) continue;

            String name = parser.getName();
            if ("pPr".equals(name)) {
                alignment = readParagraphProperties(parser);
            } else if ("r".equals(name)) {
                content.append(readRun(parser));
            } else if ("hyperlink".equals(name) || "sdtContent".equals(name)
                    || "smartTag".equals(name)) {
                parseInlineContainer(parser, name, content);
            } else if ("bookmarkStart".equals(name) || "bookmarkEnd".equals(name)
                    || "proofErr".equals(name)) {
                skipElement(parser);
            } else {
                skipElement(parser);
            }
        }

        html.append("<p");
        if (!"left".equals(alignment)) {
            html.append(" style=\"text-align:").append(alignment).append("\"");
        }
        html.append(">");
        if (content.length() == 0) html.append("<br/>");
        else html.append(content);
        html.append("</p>");
    }

    private static void parseInlineContainer(
            XmlPullParser parser, String endTag, StringBuilder html
    ) throws Exception {
        while (true) {
            int event = parser.next();
            if (event == XmlPullParser.END_DOCUMENT) return;
            if (event == XmlPullParser.END_TAG && endTag.equals(parser.getName())) return;
            if (event != XmlPullParser.START_TAG) continue;
            String name = parser.getName();
            if ("r".equals(name)) {
                html.append(readRun(parser));
            } else if ("hyperlink".equals(name) || "sdtContent".equals(name)
                    || "smartTag".equals(name)) {
                parseInlineContainer(parser, name, html);
            } else {
                skipElement(parser);
            }
        }
    }

    private static String readParagraphProperties(XmlPullParser parser) throws Exception {
        String alignment = "left";
        while (true) {
            int event = parser.next();
            if (event == XmlPullParser.END_DOCUMENT) return alignment;
            if (event == XmlPullParser.END_TAG && "pPr".equals(parser.getName())) return alignment;
            if (event != XmlPullParser.START_TAG) continue;
            String name = parser.getName();
            if ("jc".equals(name)) {
                String value = attribute(parser, "val");
                if ("center".equals(value)) alignment = "center";
                else if ("right".equals(value) || "end".equals(value)) alignment = "right";
                else if ("both".equals(value) || "distribute".equals(value)) alignment = "justify";
                skipElement(parser);
            } else {
                skipElement(parser);
            }
        }
    }

    private static String readRun(XmlPullParser parser) throws Exception {
        RunStyle style = new RunStyle();
        StringBuilder text = new StringBuilder();
        while (true) {
            int event = parser.next();
            if (event == XmlPullParser.END_DOCUMENT) break;
            if (event == XmlPullParser.END_TAG && "r".equals(parser.getName())) break;
            if (event != XmlPullParser.START_TAG) continue;

            String name = parser.getName();
            if ("rPr".equals(name)) {
                readRunProperties(parser, style);
            } else if ("t".equals(name)) {
                text.append(parser.nextText());
            } else if ("tab".equals(name)) {
                text.append('\t');
                skipElement(parser);
            } else if ("br".equals(name) || "cr".equals(name)) {
                text.append('\n');
                skipElement(parser);
            } else {
                skipElement(parser);
            }
        }
        if (text.length() == 0) return "";
        return runToHtml(text.toString(), style);
    }

    private static void readRunProperties(XmlPullParser parser, RunStyle style) throws Exception {
        while (true) {
            int event = parser.next();
            if (event == XmlPullParser.END_DOCUMENT) return;
            if (event == XmlPullParser.END_TAG && "rPr".equals(parser.getName())) return;
            if (event != XmlPullParser.START_TAG) continue;

            String name = parser.getName();
            if ("b".equals(name)) {
                style.bold = enabled(attribute(parser, "val"));
                skipElement(parser);
            } else if ("i".equals(name)) {
                style.italic = enabled(attribute(parser, "val"));
                skipElement(parser);
            } else if ("u".equals(name)) {
                style.underline = !"none".equals(attribute(parser, "val"));
                skipElement(parser);
            } else if ("strike".equals(name) || "dstrike".equals(name)) {
                style.strike = enabled(attribute(parser, "val"));
                skipElement(parser);
            } else if ("color".equals(name)) {
                String value = attribute(parser, "val");
                if (value != null && value.matches("(?i)[0-9a-f]{6}")) {
                    style.color = "#" + value;
                }
                skipElement(parser);
            } else if ("highlight".equals(name)) {
                String value = attribute(parser, "val");
                style.highlight = highlightColor(value);
                skipElement(parser);
            } else if ("sz".equals(name)) {
                try {
                    style.fontSizePt = Float.parseFloat(attribute(parser, "val")) / 2f;
                } catch (Exception ignored) { }
                skipElement(parser);
            } else if ("rFonts".equals(name)) {
                String font = attribute(parser, "ascii");
                if (font == null) font = attribute(parser, "hAnsi");
                if (font != null) style.fontFamily = font;
                skipElement(parser);
            } else if ("vertAlign".equals(name)) {
                style.vertical = attribute(parser, "val");
                skipElement(parser);
            } else {
                skipElement(parser);
            }
        }
    }

    private static void parseTable(XmlPullParser parser, StringBuilder html) throws Exception {
        html.append("<table><tbody>");
        while (true) {
            int event = parser.next();
            if (event == XmlPullParser.END_DOCUMENT) break;
            if (event == XmlPullParser.END_TAG && "tbl".equals(parser.getName())) break;
            if (event == XmlPullParser.START_TAG && "tr".equals(parser.getName())) {
                parseTableRow(parser, html);
            } else if (event == XmlPullParser.START_TAG) {
                skipElement(parser);
            }
        }
        html.append("</tbody></table>");
    }

    private static void parseTableRow(XmlPullParser parser, StringBuilder html) throws Exception {
        html.append("<tr>");
        while (true) {
            int event = parser.next();
            if (event == XmlPullParser.END_DOCUMENT) break;
            if (event == XmlPullParser.END_TAG && "tr".equals(parser.getName())) break;
            if (event == XmlPullParser.START_TAG && "tc".equals(parser.getName())) {
                parseTableCell(parser, html);
            } else if (event == XmlPullParser.START_TAG) {
                skipElement(parser);
            }
        }
        html.append("</tr>");
    }

    private static void parseTableCell(XmlPullParser parser, StringBuilder html) throws Exception {
        html.append("<td>");
        boolean hasContent = false;
        while (true) {
            int event = parser.next();
            if (event == XmlPullParser.END_DOCUMENT) break;
            if (event == XmlPullParser.END_TAG && "tc".equals(parser.getName())) break;
            if (event == XmlPullParser.START_TAG && "p".equals(parser.getName())) {
                parseParagraph(parser, html);
                hasContent = true;
            } else if (event == XmlPullParser.START_TAG && "tbl".equals(parser.getName())) {
                parseTable(parser, html);
                hasContent = true;
            } else if (event == XmlPullParser.START_TAG) {
                skipElement(parser);
            }
        }
        if (!hasContent) html.append("<p><br/></p>");
        html.append("</td>");
    }

    private static void skipElement(XmlPullParser parser) throws Exception {
        if (parser.getEventType() != XmlPullParser.START_TAG) return;
        String name = parser.getName();
        int depth = 1;
        while (depth > 0) {
            int event = parser.next();
            if (event == XmlPullParser.END_DOCUMENT) return;
            if (event == XmlPullParser.START_TAG) depth++;
            else if (event == XmlPullParser.END_TAG) depth--;
        }
    }

    private static String attribute(XmlPullParser parser, String name) {
        String value = parser.getAttributeValue(WORD_NS, name);
        if (value != null) return value;
        value = parser.getAttributeValue(null, name);
        if (value != null) return value;
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            if (name.equals(parser.getAttributeName(i))) return parser.getAttributeValue(i);
        }
        return null;
    }

    private static boolean enabled(String value) {
        return value == null || (!"0".equals(value) && !"false".equalsIgnoreCase(value)
                && !"off".equalsIgnoreCase(value));
    }

    private static String highlightColor(String value) {
        if ("yellow".equals(value)) return "#ffff00";
        if ("green".equals(value)) return "#00ff00";
        if ("cyan".equals(value)) return "#00ffff";
        if ("magenta".equals(value)) return "#ff00ff";
        if ("red".equals(value)) return "#ff0000";
        if ("blue".equals(value)) return "#0000ff";
        if ("darkBlue".equals(value)) return "#00008b";
        if ("darkRed".equals(value)) return "#8b0000";
        if ("darkGreen".equals(value)) return "#006400";
        if ("darkGray".equals(value)) return "#555555";
        if ("lightGray".equals(value)) return "#dddddd";
        return null;
    }

    private static String runToHtml(String text, RunStyle style) {
        StringBuilder out = new StringBuilder();
        StringBuilder css = new StringBuilder();
        if (style.fontSizePt > 0) css.append("font-size:").append(style.fontSizePt).append("pt;");
        if (style.color != null) css.append("color:").append(style.color).append(";");
        if (style.highlight != null) css.append("background-color:").append(style.highlight).append(";");
        if (style.fontFamily != null) {
            css.append("font-family:'").append(style.fontFamily.replace("'", "")).append("';");
        }
        if (css.length() > 0) out.append("<span style=\"").append(css).append("\">");
        if (style.bold) out.append("<strong>");
        if (style.italic) out.append("<em>");
        if (style.underline) out.append("<u>");
        if (style.strike) out.append("<s>");
        if ("superscript".equals(style.vertical)) out.append("<sup>");
        if ("subscript".equals(style.vertical)) out.append("<sub>");

        String[] lines = text.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) out.append("<br/>");
            out.append(escapeHtml(lines[i]).replace("\t", "&#9;"));
        }

        if ("superscript".equals(style.vertical)) out.append("</sup>");
        if ("subscript".equals(style.vertical)) out.append("</sub>");
        if (style.strike) out.append("</s>");
        if (style.underline) out.append("</u>");
        if (style.italic) out.append("</em>");
        if (style.bold) out.append("</strong>");
        if (css.length() > 0) out.append("</span>");
        return out.toString();
    }

    public static byte[] writeDocx(String html, Map<String, byte[]> originalEntries)
            throws Exception {
        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
        if (originalEntries != null) entries.putAll(originalEntries);

        if (!entries.containsKey("[Content_Types].xml")) entries.put("[Content_Types].xml", CONTENT_TYPES.getBytes(StandardCharsets.UTF_8));
        if (!entries.containsKey("_rels/.rels")) entries.put("_rels/.rels", ROOT_RELS.getBytes(StandardCharsets.UTF_8));
        entries.put("word/document.xml", htmlToDocumentXml(html).getBytes(StandardCharsets.UTF_8));
        if (!entries.containsKey("word/styles.xml")) {
            entries.put("word/styles.xml", STYLES.getBytes(StandardCharsets.UTF_8));
        }
        if (!entries.containsKey("word/_rels/document.xml.rels")) {
            entries.put("word/_rels/document.xml.rels", DOCUMENT_RELS.getBytes(StandardCharsets.UTF_8));
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(output);
        for (Map.Entry<String, byte[]> item : entries.entrySet()) {
            ZipEntry entry = new ZipEntry(item.getKey());
            zip.putNextEntry(entry);
            zip.write(item.getValue());
            zip.closeEntry();
        }
        zip.finish();
        zip.close();
        return output.toByteArray();
    }

    private static String htmlToDocumentXml(String html) {
        org.jsoup.nodes.Document parsed = Jsoup.parseBodyFragment(html == null ? "" : html);
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<w:document xmlns:w=\"").append(WORD_NS)
                .append("\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
        xml.append("<w:body>");
        for (Node node : parsed.body().childNodes()) {
            appendBlock(node, xml);
        }
        if (!xml.toString().contains("<w:p")) {
            xml.append("<w:p/>");
        }
        xml.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>")
                .append("<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" ")
                .append("w:left=\"1440\" w:header=\"720\" w:footer=\"720\" w:gutter=\"0\"/>")
                .append("</w:sectPr></w:body></w:document>");
        return xml.toString();
    }

    private static void appendBlock(Node node, StringBuilder xml) {
        if (node instanceof TextNode) {
            String text = ((TextNode) node).getWholeText();
            if (!text.trim().isEmpty()) appendParagraph(null, text, xml);
            return;
        }
        if (!(node instanceof Element)) return;
        Element element = (Element) node;
        String tag = element.tagName().toLowerCase(Locale.ROOT);

        if ("table".equals(tag)) {
            appendTable(element, xml);
        } else if ("ul".equals(tag) || "ol".equals(tag)) {
            int index = 1;
            for (Element item : element.children()) {
                if ("li".equals(item.tagName())) {
                    String prefix = "ul".equals(tag) ? "• " : (index++) + ". ";
                    appendParagraph(item, prefix, xml);
                } else {
                    appendBlock(item, xml);
                }
            }
        } else if ("p".equals(tag) || "div".equals(tag) || "h1".equals(tag)
                || "h2".equals(tag) || "h3".equals(tag) || "h4".equals(tag)
                || "li".equals(tag) || "blockquote".equals(tag)) {
            boolean hasNestedBlocks = false;
            for (Element child : element.children()) {
                String childTag = child.tagName().toLowerCase(Locale.ROOT);
                if ("p".equals(childTag) || "table".equals(childTag)
                        || "ul".equals(childTag) || "ol".equals(childTag)) {
                    hasNestedBlocks = true;
                    break;
                }
            }
            if (hasNestedBlocks && !"p".equals(tag)) {
                for (Node child : element.childNodes()) appendBlock(child, xml);
            } else {
                String prefix = "li".equals(tag) ? "• " : "";
                appendParagraph(element, prefix, xml);
            }
        } else {
            appendParagraph(element, "", xml);
        }
    }

    private static void appendParagraph(Element element, String prefix, StringBuilder xml) {
        xml.append("<w:p>");
        String alignment = element == null ? "" : cssValue(element.attr("style"), "text-align");
        if (!alignment.isEmpty()) {
            String value = alignment.toLowerCase(Locale.ROOT);
            if ("left".equals(value) || "center".equals(value) || "right".equals(value)
                    || "justify".equals(value)) {
                xml.append("<w:pPr><w:jc w:val=\"").append(value).append("\"/></w:pPr>");
            }
        }
        if (prefix != null && !prefix.isEmpty()) {
            appendRun(prefix, new RunStyle(), xml);
        }
        if (element != null) {
            RunStyle initial = new RunStyle();
            String defaultFamily = cssValue(element.attr("style"), "font-family");
            if (!defaultFamily.isEmpty()) {
                initial.fontFamily = defaultFamily.replace("\"", "").replace("'", "");
            }
            String tag = element.tagName().toLowerCase(Locale.ROOT);
            if ("h1".equals(tag)) { initial.bold = true; initial.fontSizePt = 20; }
            else if ("h2".equals(tag)) { initial.bold = true; initial.fontSizePt = 16; }
            else if ("h3".equals(tag)) { initial.bold = true; initial.fontSizePt = 14; }
            for (Node child : element.childNodes()) appendInline(child, initial, xml);
        }
        xml.append("</w:p>");
    }

    private static void appendInline(Node node, RunStyle inherited, StringBuilder xml) {
        if (node instanceof TextNode) {
            appendRun(((TextNode) node).getWholeText(), inherited, xml);
            return;
        }
        if (!(node instanceof Element)) return;
        Element element = (Element) node;
        String tag = element.tagName().toLowerCase(Locale.ROOT);
        if ("br".equals(tag)) {
            xml.append("<w:r><w:br/></w:r>");
            return;
        }
        if ("img".equals(tag)) return;

        RunStyle style = inherited.copy();
        if ("b".equals(tag) || "strong".equals(tag)) style.bold = true;
        if ("i".equals(tag) || "em".equals(tag)) style.italic = true;
        if ("u".equals(tag)) style.underline = true;
        if ("s".equals(tag) || "strike".equals(tag) || "del".equals(tag)) style.strike = true;
        if ("sup".equals(tag)) style.vertical = "superscript";
        if ("sub".equals(tag)) style.vertical = "subscript";
        if ("font".equals(tag) && element.hasAttr("face")) style.fontFamily = element.attr("face");
        if ("font".equals(tag) && element.hasAttr("size")) {
            String size = element.attr("size");
            String[] points = {"8", "10", "12", "14", "18", "24", "36"};
            try {
                int index = Integer.parseInt(size) - 1;
                if (index >= 0 && index < points.length) {
                    style.fontSizePt = Float.parseFloat(points[index]);
                }
            } catch (Exception ignored) { }
        }

        String css = element.attr("style");
        String weight = cssValue(css, "font-weight");
        String fontStyle = cssValue(css, "font-style");
        String decoration = cssValue(css, "text-decoration");
        if ("bold".equalsIgnoreCase(weight) || "700".equals(weight)
                || "800".equals(weight) || "900".equals(weight)) style.bold = true;
        if ("italic".equalsIgnoreCase(fontStyle)) style.italic = true;
        if (decoration.contains("underline")) style.underline = true;
        if (decoration.contains("line-through")) style.strike = true;

        String color = cssValue(css, "color");
        if (color.matches("(?i)#[0-9a-f]{6}")) style.color = color;
        String background = cssValue(css, "background-color");
        if (background.matches("(?i)#[0-9a-f]{6}")) style.highlight = background;
        String fontSize = cssValue(css, "font-size");
        if (!fontSize.isEmpty()) style.fontSizePt = parseFontSize(fontSize, style.fontSizePt);
        String family = cssValue(css, "font-family");
        if (!family.isEmpty()) style.fontFamily = family.replace("\"", "").replace("'", "");

        for (Node child : element.childNodes()) appendInline(child, style, xml);
    }

    private static void appendTable(Element table, StringBuilder xml) {
        xml.append("<w:tbl><w:tblPr><w:tblBorders>")
                .append("<w:top w:val=\"single\" w:sz=\"4\" w:color=\"B7C4BE\"/>")
                .append("<w:left w:val=\"single\" w:sz=\"4\" w:color=\"B7C4BE\"/>")
                .append("<w:bottom w:val=\"single\" w:sz=\"4\" w:color=\"B7C4BE\"/>")
                .append("<w:right w:val=\"single\" w:sz=\"4\" w:color=\"B7C4BE\"/>")
                .append("<w:insideH w:val=\"single\" w:sz=\"4\" w:color=\"B7C4BE\"/>")
                .append("<w:insideV w:val=\"single\" w:sz=\"4\" w:color=\"B7C4BE\"/>")
                .append("</w:tblBorders></w:tblPr>");
        for (Element row : table.select("tr")) {
            xml.append("<w:tr>");
            for (Element cell : row.children()) {
                if (!"td".equals(cell.tagName()) && !"th".equals(cell.tagName())) continue;
                xml.append("<w:tc><w:tcPr><w:tcW w:w=\"0\" w:type=\"auto\"/></w:tcPr>");
                boolean hasParagraph = false;
                for (Node child : cell.childNodes()) {
                    if (child instanceof Element && "table".equals(((Element) child).tagName())) {
                        appendTable((Element) child, xml);
                    } else if (child instanceof Element && "p".equals(((Element) child).tagName())) {
                        appendParagraph((Element) child, "", xml);
                        hasParagraph = true;
                    } else if (child instanceof TextNode) {
                        String text = ((TextNode) child).getWholeText();
                        if (!text.trim().isEmpty()) {
                            appendParagraph(null, text, xml);
                            hasParagraph = true;
                        }
                    } else if (child instanceof Element) {
                        appendParagraph((Element) child, "", xml);
                        hasParagraph = true;
                    }
                }
                if (!hasParagraph) xml.append("<w:p/>");
                xml.append("</w:tc>");
            }
            xml.append("</w:tr>");
        }
        xml.append("</w:tbl>");
    }

    private static void appendRun(String text, RunStyle style, StringBuilder xml) {
        if (text == null || text.isEmpty()) return;
        String[] lines = text.split("\\n", -1);
        for (int line = 0; line < lines.length; line++) {
            if (line > 0) xml.append("<w:r><w:br/></w:r>");
            String value = lines[line];
            if (value.isEmpty()) continue;
            xml.append("<w:r>");
            boolean hasProps = style.bold || style.italic || style.underline || style.strike
                    || style.fontSizePt > 0 || style.color != null || style.highlight != null
                    || style.fontFamily != null || style.vertical != null;
            if (hasProps) {
                xml.append("<w:rPr>");
                if (style.bold) xml.append("<w:b/>");
                if (style.italic) xml.append("<w:i/>");
                if (style.underline) xml.append("<w:u w:val=\"single\"/>");
                if (style.strike) xml.append("<w:strike/>");
                if (style.fontSizePt > 0) {
                    int halfPoints = Math.max(12, Math.min(144, Math.round(style.fontSizePt * 2)));
                    xml.append("<w:sz w:val=\"").append(halfPoints).append("\"/>");
                }
                if (style.color != null) {
                    xml.append("<w:color w:val=\"").append(style.color.replace("#", ""))
                            .append("\"/>");
                }
                if (style.highlight != null) {
                    xml.append("<w:shd w:val=\"clear\" w:fill=\"")
                            .append(style.highlight.replace("#", "")).append("\"/>");
                }
                if (style.fontFamily != null) {
                    String font = xmlEscape(style.fontFamily);
                    xml.append("<w:rFonts w:ascii=\"").append(font)
                            .append("\" w:hAnsi=\"").append(font).append("\"/>");
                }
                if ("superscript".equals(style.vertical)) {
                    xml.append("<w:vertAlign w:val=\"superscript\"/>");
                } else if ("subscript".equals(style.vertical)) {
                    xml.append("<w:vertAlign w:val=\"subscript\"/>");
                }
                xml.append("</w:rPr>");
            }
            xml.append("<w:t xml:space=\"preserve\">").append(xmlEscape(value))
                    .append("</w:t></w:r>");
        }
    }

    public static String plainTextToHtml(String text) {
        String value = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder html = new StringBuilder();
        String[] lines = value.split("\\n", -1);
        for (String line : lines) {
            html.append("<p>");
            html.append(escapeHtml(line).replace("\t", "&#9;"));
            if (line.isEmpty()) html.append("<br/>");
            html.append("</p>");
        }
        if (html.length() == 0) html.append("<p></p>");
        return html.toString();
    }

    public static String htmlToPlainText(String html) {
        org.jsoup.nodes.Document parsed = Jsoup.parseBodyFragment(html == null ? "" : html);
        StringBuilder output = new StringBuilder();
        for (Node node : parsed.body().childNodes()) appendPlainText(node, output);
        while (output.length() > 0 && output.charAt(output.length() - 1) == '\n') {
            output.setLength(output.length() - 1);
        }
        return output.toString();
    }

    private static void appendPlainText(Node node, StringBuilder output) {
        if (node instanceof TextNode) {
            output.append(((TextNode) node).getWholeText());
            return;
        }
        if (!(node instanceof Element)) return;
        Element element = (Element) node;
        String tag = element.tagName().toLowerCase(Locale.ROOT);
        if ("br".equals(tag)) {
            output.append('\n');
            return;
        }
        boolean block = "p".equals(tag) || "div".equals(tag) || "li".equals(tag)
                || "tr".equals(tag) || "h1".equals(tag) || "h2".equals(tag)
                || "h3".equals(tag) || "blockquote".equals(tag);
        for (Node child : element.childNodes()) appendPlainText(child, output);
        if (block && (output.length() == 0 || output.charAt(output.length() - 1) != '\n')) {
            output.append('\n');
        }
    }

    private static String cssValue(String css, String property) {
        if (css == null) return "";
        String[] declarations = css.split(";");
        for (String declaration : declarations) {
            int separator = declaration.indexOf(':');
            if (separator < 0) continue;
            if (property.equalsIgnoreCase(declaration.substring(0, separator).trim())) {
                return declaration.substring(separator + 1).trim();
            }
        }
        return "";
    }

    private static float parseFontSize(String value, float fallback) {
        try {
            String clean = value.toLowerCase(Locale.ROOT).trim();
            if (clean.endsWith("pt")) return Float.parseFloat(clean.substring(0, clean.length() - 2));
            if (clean.endsWith("px")) return Float.parseFloat(clean.substring(0, clean.length() - 2)) * 0.75f;
            return Float.parseFloat(clean);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String xmlEscape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static final class RunStyle {
        boolean bold;
        boolean italic;
        boolean underline;
        boolean strike;
        float fontSizePt;
        String color;
        String highlight;
        String fontFamily;
        String vertical;

        RunStyle copy() {
            RunStyle copy = new RunStyle();
            copy.bold = bold;
            copy.italic = italic;
            copy.underline = underline;
            copy.strike = strike;
            copy.fontSizePt = fontSizePt;
            copy.color = color;
            copy.highlight = highlight;
            copy.fontFamily = fontFamily;
            copy.vertical = vertical;
            return copy;
        }
    }

    private static final String CONTENT_TYPES =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
            + "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>"
            + "</Types>";

    private static final String ROOT_RELS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
            + "</Relationships>";

    private static final String DOCUMENT_RELS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
            + "</Relationships>";

    private static final String STYLES =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<w:styles xmlns:w=\"" + WORD_NS + "\">"
            + "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:eastAsia=\"Noto Sans Devanagari\"/></w:rPr></w:rPrDefault></w:docDefaults>"
            + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>"
            + "</w:styles>";
}
