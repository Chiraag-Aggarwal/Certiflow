"use strict";
const PROJECT_URL="https://eudohdwwzaqotjxiehqe.supabase.co";
const PUBLIC_KEY="sb_publishable_4mcpdqeCz1Jej8i40r-ZIw_cMVaEuBB";
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const FILE_PATH=/^[0-9a-f-]{36}\/[0-9a-f-]{36}\.(pdf|png|jpg|jpeg|gif|webp|txt)$/;
const $=id=>document.getElementById(id);
let currentToken="",requestVersion=0;
function safeLink(value){try{const u=new URL(value);return ["http:","https:"].includes(u.protocol)&&!u.username&&!u.password?u.href:null;}catch{return null;}}
async function request(url,options){const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),30000);try{return await fetch(url,{...options,signal:controller.signal});}finally{clearTimeout(timer);}}
function showState(title,copy,retry=false){$("state").hidden=false;$("publication").hidden=true;$("state-title").textContent=title;$("state-copy").textContent=copy;$("retry").hidden=!retry;document.title="Shared work · Certiflow";}
async function load(){
  const version=++requestVersion;currentToken=location.hash.slice(1).trim();
  if(!currentToken){showState("A place for work worth sharing.","Open a public link shared by a Certiflow student to view their project, achievements, or application summary.");return;}
  if(!UUID.test(currentToken)){showState("This link isn’t valid.","Check that you copied the full public link from Certiflow, or ask the student for a new one.");return;}
  showState("Opening shared work…","Loading only the details and files selected for this publication.");
  try{
    const response=await request(PROJECT_URL+"/rest/v1/rpc/get_public_share",{method:"POST",headers:{apikey:PUBLIC_KEY,"Content-Type":"application/json"},body:JSON.stringify({share_token:currentToken}),credentials:"omit",cache:"no-store",referrerPolicy:"no-referrer"});
    if(!response.ok)throw new Error("unavailable");const item=await response.json();if(version!==requestVersion)return;
    if(!item){showState("This publication is no longer available.","Its owner may have made it private, changed its audience, or removed it. Ask them for a current link.");return;}
    render(item);
  }catch{if(version===requestVersion)showState("We couldn’t open this publication.","Check your connection and try again. The sharing service may be temporarily unavailable.",true);}
}
function render(item){
  $("state").hidden=true;$("publication").hidden=false;$("title").textContent=item.title||"Shared work";document.title=(item.title||"Shared work")+" · Certiflow";
  $("category").textContent=item.source_kind==="NOC"?"NOC APPLICATION · "+(item.category||""):(item.category||"STUDENT WORK");
  let date="";if(item.published_at){const d=new Date(item.published_at);if(!Number.isNaN(d.getTime()))date=" · "+new Intl.DateTimeFormat(undefined,{dateStyle:"medium"}).format(d);}
  $("byline").textContent="Shared by "+(item.display_name||"a student")+date;
  $("summary").textContent=item.summary||"The owner has chosen to share this item without a public summary.";
  $("status").hidden=item.source_kind!=="NOC";$("status").textContent="CURRENT STATUS: "+(item.status||"Unavailable");
  $("disclosure").textContent=item.source_kind==="NOC"?"The summary and display name are supplied by the student. The application type and current status come from the original application. Private reasons, staff notes and account details are not published.":"This work and display name are supplied by the student. Publication does not independently verify their achievements or qualifications.";
  const link=safeLink(item.link);$("external-link").hidden=!link;if(link)$("external-link").href=link;else $("external-link").removeAttribute("href");
  $("files").replaceChildren();$("file-feedback").textContent="";
  const files=Array.isArray(item.files)?item.files.filter(f=>f&&FILE_PATH.test(f.stored_name||"")):[];$("files-section").hidden=files.length===0;
  for(const file of files){const row=document.createElement("li"),name=document.createElement("span"),detail=document.createElement("span"),button=document.createElement("button");name.className="file-name";name.textContent=file.title||file.file_name||"Supporting file";detail.className="file-detail";detail.textContent=file.file_name||"Selected attachment";name.append(detail);button.type="button";button.textContent="Download";button.setAttribute("aria-label","Download "+(file.file_name||"supporting file"));button.addEventListener("click",()=>download(file,button));row.append(name,button);$("files").append(row);}
}
async function download(file,button){
  button.disabled=true;button.textContent="Downloading…";$("file-feedback").textContent="";
  try{
    const response=await request(PROJECT_URL+"/storage/v1/object/authenticated/portfolios/"+file.stored_name,{headers:{apikey:PUBLIC_KEY},credentials:"omit",cache:"no-store",referrerPolicy:"no-referrer"});
    if(!response.ok)throw new Error("not shared");const blob=await response.blob(),url=URL.createObjectURL(blob),a=document.createElement("a");a.href=url;a.download=(file.file_name||"supporting-file").replace(/[\\/]/g,"_");a.rel="noopener";document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),30000);
  }catch{$("file-feedback").textContent="This file could not be downloaded. It may no longer be shared, or the connection may have failed.";}
  finally{button.disabled=false;button.textContent="Download";}
}
$("retry").addEventListener("click",load);window.addEventListener("hashchange",load);load();
