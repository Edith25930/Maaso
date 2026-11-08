<?php
header('Content-Type: text/html; charset=utf-8');
header('X-Content-Type-Options: nosniff');
header('X-Frame-Options: DENY');
header('Referrer-Policy: no-referrer');
header('Cache-Control: no-store');
header("Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'");
?>
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>PCA case management</title>
  <style>
    :root{font:16px system-ui,sans-serif;color:#17243a;background:#f3f6fa;--navy:#17345c;--line:#dce3ed;--muted:#64748b}
    *{box-sizing:border-box}body{margin:0}header{background:var(--navy);color:white;padding:18px max(20px,calc((100% - 1240px)/2));display:flex;justify-content:space-between;align-items:center}
    main{max-width:1240px;margin:24px auto;padding:0 18px}.panel,.metric{background:white;border:1px solid var(--line);border-radius:12px;padding:18px;margin-bottom:16px}
    h1{font-size:1.3rem;margin:0}h2{font-size:1.05rem;margin:0 0 14px}.muted{color:var(--muted)}.hidden{display:none!important}
    input,select,textarea,button{font:inherit;border:1px solid #cbd5e1;border-radius:7px;padding:9px;background:white;color:inherit}button{cursor:pointer;background:var(--navy);color:white;border-color:var(--navy)}button.secondary{background:white;color:var(--navy)}
    button:disabled{opacity:.5;cursor:not-allowed}.login{max-width:440px;margin:9vh auto}.login input{width:100%;margin:7px 0}.login button{width:100%;margin-top:12px}
    .metrics{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:14px}.metric strong{font-size:1.8rem;display:block}.charts{display:grid;grid-template-columns:1fr 1fr;gap:14px}
    .filters{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:14px}.filters input[type=search]{flex:1;min-width:180px}table{border-collapse:collapse;width:100%;background:white}th,td{text-align:left;padding:11px;border-bottom:1px solid var(--line);vertical-align:top}th{font-size:.86rem;color:var(--muted)}
    .table-wrap{overflow:auto}.gcr{font-weight:700;color:var(--navy);cursor:pointer;text-decoration:underline}.pagination{display:flex;justify-content:flex-end;align-items:center;gap:12px;margin-top:14px}
    canvas{width:100%;height:250px}.status{display:inline-block;border-radius:20px;background:#e7eef8;padding:4px 9px;font-size:.85rem}
    .priority{display:inline-block;border-radius:20px;padding:4px 9px;font-size:.85rem;text-transform:capitalize}.priority-urgent{background:#fee2e2;color:#991b1b}.priority-high{background:#ffedd5;color:#9a3412}.priority-normal{background:#e7eef8;color:#17345c}.priority-low{background:#dcfce7;color:#166534}
    dialog{border:0;border-radius:12px;width:min(760px,94vw);max-height:90vh;padding:0;box-shadow:0 18px 60px #12223855}dialog::backdrop{background:#10182799}.modal-head{padding:16px 20px;background:var(--navy);color:white;display:flex;justify-content:space-between;align-items:center}
    .modal-body{padding:20px;overflow:auto;max-height:calc(90vh - 60px)}.grid{display:grid;grid-template-columns:repeat(2,1fr);gap:10px}.field{padding:10px;background:#f5f7fa;border-radius:8px;overflow-wrap:anywhere}.field b{display:block;font-size:.8rem;color:var(--muted);margin-bottom:3px}
    .actions{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin:16px 0}.actions form{display:grid;gap:8px}.timeline{border-left:2px solid var(--line);padding-left:14px;margin:10px 0}.event{margin:12px 0}.event time{font-size:.82rem;color:var(--muted)}ul{padding-left:20px}
    .error{color:#b42318;margin-top:8px}.notice{padding:10px;border-radius:8px;background:#fff7ed;color:#9a3412;margin-bottom:12px}
    @media(max-width:760px){.metrics{grid-template-columns:1fr}.charts{grid-template-columns:1fr}.actions,.grid{grid-template-columns:1fr}header{padding:16px 18px}}
  </style>
</head>
<body>
<header><h1>Police Complaints Authority — Case Management</h1><button id="logout" class="secondary hidden">Sign out</button></header>
<main>
  <section id="loginPanel" class="panel login">
    <h2>Staff sign in</h2>
    <p class="muted">Use your PCA staff account.</p>
    <form id="loginForm"><input id="identifier" autocomplete="username" placeholder="Email or phone" required>
      <input id="password" type="password" autocomplete="current-password" placeholder="Password" required>
      <button>Sign in</button><div id="loginError" class="error" role="alert"></div>
    </form>
  </section>
  <section id="dashboard" class="hidden">
    <p id="roleNotice" class="muted"></p>
    <div id="loadError" class="error" role="alert"></div>
    <section class="metrics">
      <div class="metric"><span class="muted">All cases</span><strong id="totalCount">0</strong></div>
      <div class="metric"><span class="muted">Active cases</span><strong id="activeCount">0</strong></div>
      <div class="metric"><span class="muted">Closed cases</span><strong id="closedCount">0</strong></div>
      <div class="metric"><span class="muted">Overdue</span><strong id="overdueCount">0</strong></div>
      <div class="metric"><span class="muted">Due within 7 days</span><strong id="dueSoonCount">0</strong></div>
    </section>
    <section class="charts">
      <div class="panel"><h2>Cases by status</h2><canvas id="statusChart" aria-label="Bar chart of cases by status"></canvas></div>
      <div class="panel"><h2>Cases by complaint type</h2><canvas id="categoryChart" aria-label="Pie chart of cases by complaint type"></canvas></div>
    </section>
    <section class="panel">
      <h2>Complaints register</h2>
      <form id="filters" class="filters">
        <input type="search" id="search" placeholder="Search GCR, complainant or phone">
        <select id="statusFilter"><option value="">All statuses</option></select>
        <select id="categoryFilter"><option value="">All categories</option></select>
        <select id="priorityFilter"><option value="">All priorities</option><option value="urgent">Urgent</option><option value="high">High</option><option value="normal">Normal</option><option value="low">Low</option></select>
        <select id="dueWindowFilter"><option value="">All due dates</option><option value="overdue">Overdue</option><option value="due_soon">Due within 7 days</option></select>
        <label>From <input type="date" id="fromFilter"></label><label>To <input type="date" id="toFilter"></label>
        <button type="submit">Filter</button><button type="button" id="clearFilters" class="secondary">Clear</button>
        <button type="button" id="exportCsv">Download CSV</button>
      </form>
      <div class="table-wrap"><table>
        <thead><tr><th>GCR number</th><th>Complainant</th><th>Category</th><th>Status</th><th>Priority</th><th>Due date</th><th>Investigator</th><th>Submitted</th></tr></thead>
        <tbody id="complaintRows"></tbody>
      </table></div>
      <div id="emptyRows" class="muted hidden">No complaints match these filters.</div>
      <div class="pagination"><button id="previousPage" class="secondary">Previous</button><span id="pageLabel"></span><button id="nextPage" class="secondary">Next</button></div>
    </section>
  </section>
</main>
<dialog id="caseDialog"><div class="modal-head"><strong id="caseTitle">Complaint details</strong><button id="closeDialog" class="secondary">Close</button></div>
  <div class="modal-body">
    <div id="caseError" class="error" role="alert"></div>
    <div id="caseFields" class="grid"></div>
    <div class="actions"><form id="priorityForm"><h2>Set case priority</h2>
      <select id="casePriority"><option value="urgent">Urgent</option><option value="high">High</option><option value="normal">Normal</option><option value="low">Low</option></select>
      <button>Save priority</button>
    </form></div>
    <h2>Officer details</h2><div id="officerFields" class="grid"></div>
    <h2>Witnesses</h2><ul id="witnesses"></ul>
    <h2>Evidence</h2><ul id="evidence"></ul>
    <h2>Public complaint updates</h2><div id="timeline" class="timeline"></div>
    <div id="adminActions" class="actions hidden">
      <form id="assignmentForm"><h2>Assign investigator</h2><select id="investigatorSelect"></select><button>Save assignment</button></form>
    </div>
    <div class="actions"><form id="updateForm"><h2>Update complainant</h2>
      <select id="updateStatus"></select><textarea id="messageEn" maxlength="255" placeholder="Progress update in English (required)" required></textarea>
      <label>Expected completion date <input type="date" id="expectedCompletionDate"></label>
      <textarea id="messageSt" maxlength="255" placeholder="Progress update in Sesotho (optional)"></textarea><button>Publish update</button>
    </form></div>
  </div>
</dialog>
<script>
(() => {
  const api = '../';
  const state = {token: '', role: '', page: 1, pages: 1, perPage: 20, complaintId: 0, options: null};
  const $ = id => document.getElementById(id);
  const setText = (id, value) => { $(id).textContent = value == null || value === '' ? '—' : String(value); };
  async function request(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (state.token) headers.set('Authorization', `Bearer ${state.token}`);
    if (options.body && !(options.body instanceof Blob)) headers.set('Content-Type', 'application/json');
    const response = await fetch(api + path, {...options, headers});
    if (!response.ok) {
      let message = `Request failed (${response.status})`;
      try { const data = await response.json(); message = data.error || message; } catch (_) {}
      throw new Error(message);
    }
    return response;
  }
  function queryString() {
    const p = new URLSearchParams({page: state.page, per_page: state.perPage});
    [['q','search'],['status','statusFilter'],['category','categoryFilter'],['priority','priorityFilter'],['due_window','dueWindowFilter'],['from','fromFilter'],['to','toFilter']]
      .forEach(([key,id]) => { if ($(id).value) p.set(key,$(id).value); });
    return p;
  }
  function fillSelect(id, rows, label, value) {
    const select = $(id);
    rows.forEach(row => { const option = document.createElement('option'); option.value = row[value]; option.textContent = row[label]; select.append(option); });
  }
  function drawBars(canvasId, rows) {
    const canvas = $(canvasId), ctx = canvas.getContext('2d'), ratio = window.devicePixelRatio || 1;
    const width = canvas.clientWidth, height = 250; canvas.width = width * ratio; canvas.height = height * ratio; ctx.scale(ratio,ratio);
    ctx.clearRect(0,0,width,height); if (!rows.length) return;
    const max = Math.max(1,...rows.map(r => Number(r.count))), gap = 12, chartWidth = width - 36, barWidth = Math.max(12,(chartWidth-gap*(rows.length-1))/rows.length);
    rows.forEach((row,index) => { const x=18+index*(barWidth+gap), barHeight=(Number(row.count)/max)*170;
      ctx.fillStyle='#315d91';ctx.fillRect(x,190-barHeight,barWidth,barHeight);
      ctx.fillStyle='#17243a';ctx.font='12px system-ui';ctx.textAlign='center';ctx.fillText(String(row.count),x+barWidth/2,180-barHeight);
      ctx.save();ctx.translate(x+barWidth/2,205);ctx.rotate(-.35);ctx.fillText(row.label,x+barWidth/2,0);ctx.restore();
    });
  }
  function drawPie(rows) {
    const canvas=$('categoryChart'),ctx=canvas.getContext('2d'),ratio=window.devicePixelRatio||1,width=canvas.clientWidth,height=250;
    canvas.width=width*ratio;canvas.height=height*ratio;ctx.scale(ratio,ratio);ctx.clearRect(0,0,width,height);
    const total=rows.reduce((sum,row)=>sum+Number(row.count),0);if(!total){ctx.fillStyle='#64748b';ctx.fillText('No case data',16,24);return;}
    const colors=['#315d91','#e58a27','#42a58a','#ce5b5b','#8a6ac2','#5aa3c2','#a4a83b','#c76d9a'];
    let start=-Math.PI/2;const cx=Math.min(115,width*.32),cy=125,r=85;
    rows.forEach((row,index)=>{const end=start+Number(row.count)/total*Math.PI*2;ctx.beginPath();ctx.moveTo(cx,cy);ctx.arc(cx,cy,r,start,end);ctx.closePath();ctx.fillStyle=colors[index%colors.length];ctx.fill();start=end;});
    rows.forEach((row,index)=>{const y=30+index*24;if(y>height-8)return;ctx.fillStyle=colors[index%colors.length];ctx.fillRect(Math.min(225,width*.64),y-10,11,11);ctx.fillStyle='#17243a';ctx.font='12px system-ui';ctx.textAlign='left';ctx.fillText(`${row.label} (${row.count})`,Math.min(243,width*.64+18),y);});
  }
  async function loadOverview() {
    const response=await request('staff/overview.php');const data=(await response.json()).data;
    $('totalCount').textContent=data.summary.total||0;$('activeCount').textContent=data.summary.active||0;$('closedCount').textContent=data.summary.closed||0;
    $('overdueCount').textContent=data.summary.overdue||0;$('dueSoonCount').textContent=data.summary.due_soon||0;
    drawBars('statusChart',data.by_status);drawPie(data.by_category);
    if(!state.options){
      state.options=data;fillSelect('statusFilter',data.statuses,'label_en','code');fillSelect('categoryFilter',data.categories,'name_en','category_id');
      fillSelect('updateStatus',data.statuses,'label_en','code');
      const investigator=$('investigatorSelect');investigator.add(new Option('Unassigned',''));
      data.investigators.forEach(row=>investigator.add(new Option(row.full_name,row.user_id)));
    }
  }
  async function loadComplaints() {
    $('loadError').textContent='';
    const response=await request(`staff/complaints.php?${queryString()}`);const data=(await response.json()).data;
    state.pages=Math.max(1,data.pages);state.page=data.page;
    const body=$('complaintRows');body.replaceChildren();
    data.items.forEach(item=>{
      const tr=document.createElement('tr');
      const gcr=document.createElement('td'),link=document.createElement('span');link.className='gcr';link.textContent=item.gcr_number;link.onclick=()=>openCase(item.complaint_id);gcr.append(link);tr.append(gcr);
      [item.complainant_name,item.category_name,item.status_label].forEach(value=>{const td=document.createElement('td');td.textContent=value||'—';tr.append(td);});
      const priorityCell=document.createElement('td'),priority=document.createElement('span');priority.className=`priority priority-${item.case_priority}`;priority.textContent=item.case_priority;priorityCell.append(priority);tr.append(priorityCell);
      const dueCell=document.createElement('td');dueCell.textContent=item.expected_completion_date?`${item.due_label?`${item.due_label} · `:''}${item.expected_completion_date}`:'—';tr.append(dueCell);
      [item.investigator_name,item.submitted_at].forEach(value=>{const td=document.createElement('td');td.textContent=value||'—';tr.append(td);});
      body.append(tr);
    });
    $('emptyRows').classList.toggle('hidden',data.items.length!==0);
    $('pageLabel').textContent=`Page ${data.page} of ${state.pages} · ${data.total} cases`;
    $('previousPage').disabled=data.page<=1;$('nextPage').disabled=data.page>=state.pages;
  }
  function field(container, label, value) {
    const box=document.createElement('div');box.className='field';const b=document.createElement('b');b.textContent=label;const span=document.createElement('span');span.textContent=value||'—';box.append(b,span);container.append(box);
  }
  async function openCase(id) {
    state.complaintId=id;$('caseError').textContent='';$('caseFields').replaceChildren();$('officerFields').replaceChildren();
    $('witnesses').replaceChildren();$('evidence').replaceChildren();$('timeline').replaceChildren();
    $('caseTitle').textContent='Loading complaint…';$('caseDialog').showModal();
    try {
      const response=await request(`staff/detail.php?id=${encodeURIComponent(id)}`);const data=(await response.json()).data,c=data.complaint;
      $('caseTitle').textContent=`${c.gcr_number} · ${c.status_label}`;
      [['Complainant',c.complainant_display_name],['Phone',c.complainant_display_phone],['Email',c.complainant_email],['ID number',c.complainant_id_number],['Gender',c.complainant_gender],['Complainant district',c.complainant_district],['Contact location',c.complainant_location],['Category',c.category_name],['Incident date',c.incident_date],['Incident time',c.incident_time],['Incident district',c.district_name],['Incident location',c.incident_location_text],['Police station involved',c.incident_station_name],['Latitude',c.latitude],['Longitude',c.longitude],['Location shared',Number(c.location_shared)?'Yes':'No'],['Description',c.description],['Declaration confirmed',Number(c.declaration_confirmed)?'Yes':'No'],['AI assistance used',Number(c.ai_suggestion_used)?'Yes':'No'],['Status',c.status_label],['Priority',c.case_priority],['Expected completion date',c.expected_completion_date],['Investigator',c.investigator_name],['Submitted',c.submitted_at],['Last updated',c.updated_at]].forEach(([l,v])=>field($('caseFields'),l,v));
      [['Officer known',c.officer_unknown==null?'Not provided':(Number(c.officer_unknown)?'No':'Yes')],['Name',c.officer_name],['Rank',c.officer_rank],['Badge number',c.badge_number],['Station',c.officer_station_name]].forEach(([l,v])=>field($('officerFields'),l,v));
      data.witnesses.forEach(w=>{const li=document.createElement('li');li.textContent=`${w.full_name}${w.contact_number?` · ${w.contact_number}`:''}`;$('witnesses').append(li);});
      data.evidence.forEach(e=>{const li=document.createElement('li'),link=document.createElement('a');link.textContent=`${e.file_name} (${Math.ceil(Number(e.file_size_bytes)/1024)} KB)`;link.href='#';link.onclick=async event=>{event.preventDefault();await downloadEvidence(e.evidence_id,e.file_name);};li.append(link);$('evidence').append(li);});
      data.timeline.forEach(e=>{const div=document.createElement('div');div.className='event';const time=document.createElement('time');time.textContent=e.changed_at;const p=document.createElement('p');p.textContent=`${e.status_label}: ${e.message_en||''}${e.message_st?` · ${e.message_st}`:''}`;div.append(time,p);$('timeline').append(div);});
      $('adminActions').classList.toggle('hidden',state.role!=='admin');
      $('investigatorSelect').value=c.investigator_id||'';
      $('updateStatus').value=c.status_code;
      $('expectedCompletionDate').value=c.expected_completion_date||'';
      $('casePriority').value=c.case_priority;
    } catch(error) {$('caseError').textContent=error.message;}
  }
  async function downloadEvidence(id,name) {
    try {
      const response=await request(`staff/evidence.php?id=${encodeURIComponent(id)}`);const blob=await response.blob();const url=URL.createObjectURL(blob);
      const a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
    } catch(error) {$('caseError').textContent=error.message;}
  }
  async function refresh() {try{await Promise.all([loadOverview(),loadComplaints()]);}catch(error){$('loadError').textContent=error.message;}}
  function showDashboard(role) {
    state.role=role;$('loginPanel').classList.add('hidden');$('dashboard').classList.remove('hidden');$('logout').classList.remove('hidden');
    $('roleNotice').textContent=role==='admin'?'Administrator access: all submitted complaints.':'Investigator access: assigned complaints only.';
    $('adminActions').classList.toggle('hidden',role!=='admin');
    $('assignmentForm').classList.toggle('hidden',role!=='admin');
  }
  $('loginForm').addEventListener('submit',async event=>{
    event.preventDefault();$('loginError').textContent='';
    try{
      const response=await fetch('../auth/login.php',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({identifier:$('identifier').value,password:$('password').value,device_label:'pca-staff-dashboard'})});
      const body=await response.json();if(!response.ok||!body.success)throw new Error(body.error||'Sign in failed.');
      if(!['admin','investigator'].includes(body.data.role))throw new Error('This account does not have staff dashboard access.');
      state.token=body.data.token;state.role=body.data.role;showDashboard(state.role);await refresh();
    }catch(error){$('loginError').textContent=error.message;}
  });
  $('logout').onclick=async()=>{try{if(state.token)await request('auth/logout.php',{method:'POST'});}catch(error){$('loginError').textContent=error.message;}finally{state.token='';state.role='';$('dashboard').classList.add('hidden');$('loginPanel').classList.remove('hidden');$('logout').classList.add('hidden');$('password').value='';}};
  window.addEventListener('pagehide',()=>{if(!state.token)return;fetch(api+'auth/logout.php',{method:'POST',headers:{Authorization:`Bearer ${state.token}`},keepalive:true}).catch(error=>console.warn('Could not revoke staff session on page close.',error));state.token='';});
  $('filters').onsubmit=event=>{event.preventDefault();state.page=1;loadComplaints().catch(e=>$('loadError').textContent=e.message);};
  $('clearFilters').onclick=()=>{$('filters').reset();state.page=1;loadComplaints().catch(e=>$('loadError').textContent=e.message);};
  $('previousPage').onclick=()=>{if(state.page>1){state.page--;loadComplaints().catch(e=>$('loadError').textContent=e.message);}};
  $('nextPage').onclick=()=>{if(state.page<state.pages){state.page++;loadComplaints().catch(e=>$('loadError').textContent=e.message);}};
  $('exportCsv').onclick=async()=>{try{const response=await request(`staff/export.php?${queryString()}`);const blob=await response.blob();const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='pca-complaints.csv';a.click();URL.revokeObjectURL(url);}catch(e){$('loadError').textContent=e.message;}};
  $('closeDialog').onclick=()=>$('caseDialog').close();
  $('assignmentForm').onsubmit=async event=>{event.preventDefault();try{await request('staff/assign.php',{method:'POST',body:JSON.stringify({complaint_id:state.complaintId,investigator_id:$('investigatorSelect').value})});await refresh();await openCase(state.complaintId);}catch(e){$('caseError').textContent=e.message;}};
  $('priorityForm').onsubmit=async event=>{event.preventDefault();try{await request('staff/priority.php',{method:'POST',body:JSON.stringify({complaint_id:state.complaintId,priority:$('casePriority').value})});await refresh();await openCase(state.complaintId);}catch(e){$('caseError').textContent=e.message;}};
  $('updateForm').onsubmit=async event=>{event.preventDefault();try{await request('staff/update.php',{method:'POST',body:JSON.stringify({complaint_id:state.complaintId,status_code:$('updateStatus').value,message_en:$('messageEn').value.trim(),message_st:$('messageSt').value.trim(),expected_completion_date:$('expectedCompletionDate').value||null})});$('messageEn').value='';$('messageSt').value='';await refresh();await openCase(state.complaintId);}catch(e){$('caseError').textContent=e.message;}};
  window.addEventListener('resize',()=>{if(state.options){drawBars('statusChart',state.options.by_status);drawPie(state.options.by_category);}});
})();
</script>
</body>
</html>
