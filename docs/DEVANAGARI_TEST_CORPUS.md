# Devanagari test corpus

Use this corpus in Font Center and in DOCX round-trip QA. Compare each result against a reference rendering on a current Android WebView. Do not infer complete coverage from a font's name or family listing.

## 1. Independent vowels and consonants

अ आ इ ई उ ऊ ऋ ॠ ऌ ए ऐ ओ औ अं अः

क ख ग घ ङ च छ ज झ ञ ट ठ ड ढ ण त थ द ध न प फ ब भ म य र ल व श ष स ह

## 2. Matras and reordered vowel signs

का कि की कु कू कृ के कै को कौ कं कः कँ

क् + ि, कि; र् + क, र्क; क् + ष, क्ष

## 3. Reph, rakar and conjunct clusters

र्क र्क्ष प्र त्र श्र स्त्र स्त्य द्य द्व द्ध क्त ज्ञ ह्न ह्म द्र ग्र क्र फ्र

क्षत्रिय प्रार्थना दृष्टि विद्यालय श्रद्धा स्वतंत्र राष्ट्र

## 4. Nukta and extended Hindi letters

क़ ख़ ग़ ज़ ड़ ढ़ फ़ य़

क़लम ख़बर ग़ज़ल ज़रूरी सड़क पढ़ाई फ़र्क़

## 5. Nasal marks, visarga and punctuation

अँ अं अः माँ हिंदी दुःख चाँद हँसी

। ॥ ( ) [ ] “ ” ‘ ’ — … ?

## 6. Devanagari digits and currency

० १ २ ३ ४ ५ ६ ७ ८ ९

₹ १२,५००.००

## 7. Joiner and non-joiner

क्‍ष — the sequence contains U+200D ZERO WIDTH JOINER.

क्‌ष — the sequence contains U+200C ZERO WIDTH NON-JOINER.

These sequences must be retained as distinct Unicode text. Do not normalise or remove the joiner controls without a document-level rule.

## 8. Mixed-script sample

FZ Office Suite में हिन्दी typing, English words, dates 08-11-2026 और ₹ amount एक ही paragraph में test करें.

## Expected QA checks

- No missing-glyph boxes for the font's claimed coverage.
- Matras stay attached to their base consonants and are reordered correctly.
- Reph/rakar and conjunct forms do not collide or detach.
- Nukta and extended letters remain readable.
- ZWJ/ZWNJ survive open-edit-save-reopen.
- DOCX run font-family metadata is retained for the supported subset.
- TXT remains UTF-8 and preserves the Unicode sequence.
