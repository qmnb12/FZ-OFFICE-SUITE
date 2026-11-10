(function(){
"use strict";
var workbook=null,activeSheet="",activeAddress="A1",dirty=false;
var gridWrap=document.getElementById("gridWrap"),tabs=document.getElementById("tabs");
var formulaInput=document.getElementById("formulaInput"),addressLabel=document.getElementById("address"),footer=document.getElementById("footer");
var MAX_ROWS=500,MAX_COLS=40;
var sheetZoom=1,pinchStart=null;
function touchDistance(touches){
 var dx=touches[0].clientX-touches[1].clientX;
 var dy=touches[0].clientY-touches[1].clientY;
 return Math.sqrt(dx*dx+dy*dy);
}
function applySheetZoom(requested,focusX,focusY){
 var table=gridWrap.querySelector("table");
 if(!table)return;
 var next=Math.max(.3,Math.min(2.5,Math.round(requested*20)/20));
 if(Math.abs(next-sheetZoom)<.01)return;
 var ratio=next/sheetZoom;
 var left=gridWrap.scrollLeft,top=gridWrap.scrollTop;
 sheetZoom=next;
 table.style.zoom=String(sheetZoom);
 // Keep the cell under the user's fingers stationary while the sheet changes scale.
 gridWrap.scrollLeft=Math.max(0,(left+focusX)*ratio-focusX);
 gridWrap.scrollTop=Math.max(0,(top+focusY)*ratio-focusY);
}
function handlePinchStart(event){
 if(event.touches.length!==2)return;
 var rect=gridWrap.getBoundingClientRect();
 pinchStart={
  distance:Math.max(1,touchDistance(event.touches)),
  zoom:sheetZoom,
  centerX:(event.touches[0].clientX+event.touches[1].clientX)/2-rect.left,
  centerY:(event.touches[0].clientY+event.touches[1].clientY)/2-rect.top
 };
 event.preventDefault();
}
function handlePinchMove(event){
 if(!pinchStart||event.touches.length!==2)return;
 event.preventDefault();
 var rect=gridWrap.getBoundingClientRect();
 var centerX=(event.touches[0].clientX+event.touches[1].clientX)/2-rect.left;
 var centerY=(event.touches[0].clientY+event.touches[1].clientY)/2-rect.top;
 var next=pinchStart.zoom*touchDistance(event.touches)/pinchStart.distance;
 applySheetZoom(next,centerX,centerY);
}
function handlePinchEnd(event){
 if(!pinchStart||event.touches.length>=2)return;
 pinchStart=null;
 status("Sheet zoom "+Math.round(sheetZoom*100)+"% · Edit cells and formulas as usual.");
}
function status(message){
 footer.textContent=message;
 if(window.AndroidBridge&&AndroidBridge.setStatus)AndroidBridge.setStatus(message);
}
function columnName(index){
 var result="",n=index+1;
 while(n>0){var rem=(n-1)%26;result=String.fromCharCode(65+rem)+result;n=Math.floor((n-1)/26)}
 return result;
}
function cellAddress(row,col){return columnName(col)+(row+1)}
function currentSheet(){return workbook&&workbook.Sheets[activeSheet]}
function getRange(sheet){
 if(sheet["!ref"]){try{return XLSX.utils.decode_range(sheet["!ref"])}catch(e){}}
 return {s:{r:0,c:0},e:{r:29,c:11}};
}
function updateRange(sheet,row,col){
 var range=getRange(sheet);
 range.e.r=Math.max(range.e.r,row);range.e.c=Math.max(range.e.c,col);
 if(!sheet["!ref"])range.s={r:0,c:0};
 sheet["!ref"]=XLSX.utils.encode_range(range);
}
function cellFormulaValue(cell){
 if(!cell)return "";
 if(cell.f)return "="+String(cell.f);
 if(cell.v===undefined||cell.v===null)return "";
 return String(cell.v);
}
function cellDisplay(cell){
 if(!cell)return "";
 try{
  if(cell.w!==undefined&&cell.w!==null)return String(cell.w);
  if(cell.f)return "="+String(cell.f);
  return cell.v===undefined||cell.v===null?"":String(cell.v);
 }catch(e){return ""}
}
function renderTabs(){
 tabs.textContent="";
 workbook.SheetNames.forEach(function(name){
  var button=document.createElement("button");
  button.textContent=name;
  if(name===activeSheet)button.className="active";
  button.addEventListener("click",function(){
   commitInput();activeSheet=name;activeAddress="A1";renderTabs();renderGrid();
  });
  tabs.appendChild(button);
 });
}
function selectCell(address){
 activeAddress=address;addressLabel.textContent=address;
 var sheet=currentSheet(),cell=sheet&&sheet[address];
 formulaInput.value=cellFormulaValue(cell);
 var old=gridWrap.querySelector("td.cell.selected");if(old)old.classList.remove("selected");
 var selected=gridWrap.querySelector('td.cell[data-address="'+address+'"]');
 if(selected)selected.classList.add("selected");
}
function renderGrid(){
 var sheet=currentSheet();
 if(!sheet){gridWrap.textContent="No worksheet selected.";return}
 var range=getRange(sheet);
 var lastRow=Math.min(MAX_ROWS-1,Math.max(29,range.e.r));
 var lastCol=Math.min(MAX_COLS-1,Math.max(11,range.e.c));
 var table=document.createElement("table");
 table.style.zoom=String(sheetZoom);
 var thead=document.createElement("thead"),head=document.createElement("tr");
 var corner=document.createElement("th");corner.className="corner rowhead";head.appendChild(corner);
 for(var c=0;c<=lastCol;c++){
  var th=document.createElement("th");th.textContent=columnName(c);th.style.width="110px";head.appendChild(th);
 }
 thead.appendChild(head);table.appendChild(thead);
 var tbody=document.createElement("tbody");
 for(var r=0;r<=lastRow;r++){
  var tr=document.createElement("tr"),rowHead=document.createElement("th");
  rowHead.className="rowhead";rowHead.textContent=String(r+1);tr.appendChild(rowHead);
  for(var col=0;col<=lastCol;col++){
   var addr=cellAddress(r,col),cell=sheet[addr],td=document.createElement("td");
   td.className="cell";td.dataset.address=addr;td.textContent=cellDisplay(cell);
   if(addr===activeAddress)td.classList.add("selected");
   tr.appendChild(td);
  }
  tbody.appendChild(tr);
 }
 table.appendChild(tbody);gridWrap.textContent="";gridWrap.appendChild(table);
 selectCell(activeAddress);
 if(range.e.r>=MAX_ROWS||range.e.c>=MAX_COLS){
  status("Large sheet display is capped at "+MAX_ROWS+" rows and "+MAX_COLS+" columns in this editor.");
 }
}
function commitInput(){
 var sheet=currentSheet();if(!sheet)return;
 var raw=formulaInput.value,old=sheet[activeAddress];
 var cell=old?Object.assign({},old):{};
 if(raw.charAt(0)==="="&&raw.length>1){
  cell.f=raw.substring(1);delete cell.v;delete cell.w;delete cell.t;
  sheet[activeAddress]=cell;
 }else if(raw===""){
  if(old&&old.s!==undefined){
   cell.v="";cell.t="s";delete cell.f;delete cell.w;sheet[activeAddress]=cell;
  }else delete sheet[activeAddress];
 }else{
  var numeric=Number(raw);
  if(raw.trim()!==""&&Number.isFinite(numeric)){cell.v=numeric;cell.t="n"}
  else{cell.v=raw;cell.t="s"}
  delete cell.f;delete cell.w;sheet[activeAddress]=cell;
 }
 var decoded=XLSX.utils.decode_cell(activeAddress);
 updateRange(sheet,decoded.r,decoded.c);
 dirty=true;
}
function handleGridClick(event){
 var target=event.target.closest("td[data-address]");if(!target)return;
 commitInput();selectCell(target.dataset.address);
}
function addRow(){
 var sheet=currentSheet();if(!sheet)return;
 var range=getRange(sheet),row=range.e.r+1;
 if(row>=MAX_ROWS){status("This editor supports up to "+MAX_ROWS+" visible rows per worksheet.");return}
 updateRange(sheet,row,0);dirty=true;renderGrid();selectCell(cellAddress(row,0));
 status("Row added · Save a copy to keep changes.");
}
function addColumn(){
 var sheet=currentSheet();if(!sheet)return;
 var range=getRange(sheet),col=range.e.c+1;
 if(col>=MAX_COLS){status("This editor supports up to "+MAX_COLS+" visible columns per worksheet.");return}
 updateRange(sheet,0,col);dirty=true;renderGrid();selectCell(cellAddress(0,col));
 status("Column added · Save a copy to keep changes.");
}
function addSheet(){
 var index=1,name="Sheet "+index;
 while(workbook.SheetNames.indexOf(name)!==-1){index++;name="Sheet "+index}
 workbook.SheetNames.push(name);workbook.Sheets[name]={"!ref":"A1"};
 activeSheet=name;dirty=true;renderTabs();renderGrid();status("New worksheet added.");
}
function saveCopy(){
 if(!workbook){status("Workbook is not ready.");return}
 commitInput();
 try{
  status("Preparing edited XLSX copy…");
  workbook.Workbook=workbook.Workbook||{};
  workbook.Workbook.CalcPr={calcMode:"auto",fullCalcOnLoad:true,forceFullCalc:true};
  var output=XLSX.write(workbook,{bookType:"xlsx",type:"base64",cellStyles:true,compression:true});
  if(!window.AndroidBridge||!AndroidBridge.saveWorkbook){status("Save bridge is unavailable. Reopen the editor.");return}
  AndroidBridge.saveWorkbook(output);
 }catch(error){
  status("Could not prepare workbook: "+(error&&error.message?error.message:"unknown error"));
 }
}
function loadWorkbook(){
 if(!window.XLSX){status("XLSX codec could not be loaded.");gridWrap.textContent="XLSX codec is missing from the app assets.";return}
 fetch("/doc/file.xlsx",{cache:"no-store"}).then(function(response){
  if(!response.ok)throw new Error("Could not read the selected workbook.");
  return response.arrayBuffer();
 }).then(function(bytes){
  workbook=XLSX.read(bytes,{type:"array",cellStyles:true,cellDates:true,cellNF:true});
  if(!workbook.SheetNames||!workbook.SheetNames.length)throw new Error("The workbook has no worksheets.");
  activeSheet=workbook.SheetNames[0];activeAddress="A1";
  renderTabs();renderGrid();
  status("Loaded "+workbook.SheetNames.length+" worksheet(s). Edit cells/formulas, then choose Save copy.");
 }).catch(function(error){
  gridWrap.textContent=error.message||"Could not open this workbook.";
  status(error.message||"Workbook load failed.");
 });
}
gridWrap.addEventListener("click",handleGridClick);
gridWrap.addEventListener("touchstart",handlePinchStart,{passive:false});
gridWrap.addEventListener("touchmove",handlePinchMove,{passive:false});
gridWrap.addEventListener("touchend",handlePinchEnd,{passive:false});
gridWrap.addEventListener("touchcancel",handlePinchEnd,{passive:false});
formulaInput.addEventListener("keydown",function(event){
 if(event.key==="Enter"){
  event.preventDefault();commitInput();
  var decoded=XLSX.utils.decode_cell(activeAddress);
  activeAddress=cellAddress(Math.min(MAX_ROWS-1,decoded.r+1),decoded.c);
  renderGrid();formulaInput.focus();
 }
});
formulaInput.addEventListener("change",function(){commitInput();renderGrid()});
document.getElementById("save").addEventListener("click",saveCopy);
document.getElementById("addRow").addEventListener("click",addRow);
document.getElementById("addColumn").addEventListener("click",addColumn);
document.getElementById("newSheet").addEventListener("click",addSheet);
loadWorkbook();
})();