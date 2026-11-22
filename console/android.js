(function () {
  if (!window.MagpieHost) return;
  var H = window.MagpieHost;
  var st = null;
  function h(t, c, x) { var n = document.createElement(t); if (c) n.className = c; if (x != null) n.textContent = x; return n; }
  function say(m) { try { H.toast(m); } catch (e) {} }
  function read() { try { st = JSON.parse(H.state()); } catch (e) { st = null; } return st; }
  function base() { return 'http://127.0.0.1:' + ((read() && st.gatewayPort) || 3425); }

  // ---- header switch ------------------------------------------------------
  var box = h('div', 'ago-top'), dot = h('span', 'ago-dot'), text = h('div', 'ago-state', '网关'), sub = h('small');
  text.appendChild(sub);
  var sw = h('button', 'ago-switch'); sw.type = 'button'; sw.setAttribute('role', 'switch'); sw.appendChild(h('span', 'ago-knob'));
  box.append(dot, text, sw);
  function paint() { var s = read(); if (!s) return; box.classList.toggle('on', !!s.running); sw.setAttribute('aria-checked', s.running ? 'true' : 'false'); sub.textContent = s.running ? (':' + s.gatewayPort) : '已停止'; }
  sw.onclick = function () { var s = read(); if (!s) return; if (s.running) { H.stop(); say('正在停止…'); } else { H.start(); say('正在启动…'); } setTimeout(paint, 700); };

  // ---- API page -----------------------------------------------------------
  var page = h('div', 'ago-page');

  function card(title, note) {
    var c = h('section', 'ago-card');
    c.appendChild(h('h3', '', title));
    if (note) c.appendChild(h('p', 'ago-note', note));
    return c;
  }

  // endpoints
  var epCard = card('接入地址', '同一台手机上，别的 App 直接连这里；局域网设备换成手机的局域网 IP。');
  var protos = [
    ['OpenAI 兼容', '/v1'],
    ['Anthropic', '/v1/messages'],
    ['OpenAI Responses', '/v1/responses'],
    ['Gemini', '/v1beta'],
    ['向量 / 重排', '/v1/embeddings · /v1/rerank'],
  ];
  protos.forEach(function (p) {
    var row = h('div', 'ago-proto');
    var k = h('div', 'ago-proto-l');
    k.appendChild(h('b', '', p[0]));
    var u = h('span', 'ago-mono');
    u.textContent = base() + p[1];
    k.appendChild(u);
    var cp = h('button', 'ago-link', '复制');
    cp.onclick = function () { H.copy(base() + p[1]); say('已复制'); };
    row.append(k, cp);
    epCard.appendChild(row);
  });

  // listener
  var netCard = card('监听', '改了会立刻重启网关；局域网对外开放前建议先建密钥。');
  var portRow = h('div', 'ago-proto');
  var pin = h('input', 'ago-input ago-port');
  pin.type = 'number'; pin.inputMode = 'numeric';
  var s0 = {}; try { s0 = JSON.parse(H.settings()); } catch (e) {}
  pin.value = s0.port || 3425;
  var psave = h('button', 'ago-link', '保存');
  psave.onclick = function () {
    var v = parseInt(pin.value, 10);
    if (!v || v < 1024 || v > 65535) { say('端口需在 1024-65535'); return; }
    H.setPort(v); say('端口已改为 ' + v + '，网关重启中');
  };
  var pl = h('div', 'ago-proto-l'); pl.append(h('b', '', '监听端口'), pin);
  portRow.append(pl, psave);
  var lanRow = h('div', 'ago-proto');
  var lanTxt = h('div', 'ago-proto-l'); lanTxt.appendChild(h('b', '', '允许局域网访问')); lanTxt.appendChild(h('span', 'ago-mono', '0.0.0.0 — 其它设备可用这台手机的局域网 IP'));
  var lanSw = h('button', 'ago-switch small'); lanSw.appendChild(h('span', 'ago-knob'));
  lanSw.classList.toggle('on', !!s0.lan);
  lanSw.onclick = function () { var on = !lanSw.classList.contains('on'); lanSw.classList.toggle('on', on); H.setLan(on); say(on ? '已开放局域网，网关重启中' : '已关闭局域网'); };
  lanRow.append(lanTxt, lanSw);
  netCard.append(portRow, lanRow);

  // keys
  var keysCard = card('密钥', '给别的 App / 局域网设备用的凭据；本机 App 可以不留密钥直接连。');
  var form = h('div', 'ago-form');
  var nameIn = h('input', 'ago-input'); nameIn.placeholder = '名称，例如「Oprit AI」';
  var addBtn = h('button', 'ago-primary', '新建');
  form.append(nameIn, addBtn);
  var out = h('div', 'ago-out'); out.style.display = 'none';
  var list = h('div', 'ago-list');
  keysCard.append(form, out, list);

  page.append(card('API', '这是本机 magpie 网关的上游接口：OpenAI 兼容为主，也接受 Anthropic、Responses、Gemini 的形状。'), epCard, netCard, keysCard);
  page.querySelector('.ago-card h3').parentNode.classList.add('ago-hero');

  function showOut(t) { out.style.display = 'block'; out.textContent = t; }
  function test(key, slot) {
    slot.textContent = '测试中…';
    var r = {}; try { r = JSON.parse(H.testKey(key)); } catch (e) { r = { code: 0, error: String(e) }; }
    slot.textContent = r.code === 200 ? ('可用 · ' + (r.models || 0) + ' 个模型') : ('不可用 · ' + (r.code ? 'HTTP ' + r.code : (r.error || '失败')));
    slot.classList.toggle('bad', r.code !== 200);
  }
  function load() {
    if (typeof window.api !== 'function') return;
    window.api('caller-keys').then(function (o) {
      var keys = (o && o.keys) || [];
      list.replaceChildren();
      if (!keys.length) { list.appendChild(h('p', 'ago-note', '还没有密钥。')); return; }
      keys.forEach(function (k) {
        var c = h('div', 'ago-keycard');
        var head = h('div', 'ago-keyhead');
        head.appendChild(h('div', 'ago-keyname', k.name || k.id));
        var badge = h('span', 'ago-badge ' + (k.off ? 'off' : 'on'), k.off ? '已停用' : '启用');
        head.appendChild(badge);
        var id = h('div', 'ago-mono ago-keyid', k.id);
        var status = h('div', 'ago-keytest', '未测试');
        var acts = h('div', 'ago-actions');
        function act(label, fn, cls) { var b = h('button', 'ago-act' + (cls ? ' ' + cls : ''), label); b.onclick = fn; acts.appendChild(b); }
        act('测试', function () { test(k.id, status); });
        act('复制', function () { window.api('caller-keys/copy-key', { key: k.id }).then(function (r) { var s = (r && (r.secret || r.key)) || ''; H.copy(s); say('已复制'); test(s, status); }).catch(function (e) { showOut('复制失败：' + e.message); }); });
        act('重命名', function () { var v = prompt('新名称', k.name || ''); if (!v) return; window.api('caller-keys/rename-key', { key: k.id, name: v }).then(load).catch(function (e) { showOut('重命名失败：' + e.message); }); });
        act(k.off ? '启用' : '停用', function () { window.api('caller-keys/' + (k.off ? 'on-key' : 'off-key'), { key: k.id }).then(load).catch(function (e) { showOut('操作失败：' + e.message); }); });
        act('轮换', function () { if (!confirm('轮换后旧密钥立刻失效，继续？')) return; window.api('caller-keys/rotate-key', { key: k.id }).then(function (r) { var s = (r && (r.secret || r.key)) || ''; if (s) H.copy(s); showOut('新密钥（已复制）：' + s); load(); }).catch(function (e) { showOut('轮换失败：' + e.message); }); });
        act('删除', function () { if (!confirm('删除「' + (k.name || k.id) + '」？')) return; window.api('caller-keys/remove-key', { key: k.id }).then(load).catch(function (e) { showOut('删除失败：' + e.message); }); }, 'danger');
        c.append(head, id, status, acts);
        list.appendChild(c);
      });
    }).catch(function (e) { list.replaceChildren(h('p', 'ago-note', '读取失败：' + e.message)); });
  }
  addBtn.onclick = function () {
    var name = (nameIn.value || '').trim() || '新密钥';
    if (typeof window.api !== 'function') return;
    window.api('caller-keys/add-key', { name: name }).then(function (r) {
      nameIn.value = '';
      var s = (r && (r.secret || r.key)) || '';
      if (s) { H.copy(s); showOut('已创建并复制：' + s); }
      load();
    }).catch(function (e) { showOut('创建失败：' + e.message); });
  };

  // ---- tab ----------------------------------------------------------------
  var nav = document.getElementById('nav');
  var tab = h('button', 'ago-tab', 'API'); tab.type = 'button';
  var open = false;
  function showPage(on) { open = on; page.classList.toggle('open', on); tab.classList.toggle('on', on); if (on) { read(); load(); } }
  tab.onclick = function (e) { e.preventDefault(); e.stopPropagation(); showPage(!open); };
  if (nav) { nav.appendChild(tab); nav.addEventListener('click', function (e) { if (e.target.closest('button[data-view]')) showPage(false); }, true); }

  // ---- WorkBuddy check-in, inside its editor -------------------------------
  function editorRow() {
    return;
    var app0 = document.querySelector('#app') || document.body;
    var tx0 = app0.textContent || '';
    var listPage = !!document.querySelector('input[placeholder*="查找供应商"], input[placeholder*="Search providers"]');
    var inEditor = !listPage && tx0.indexOf('WorkBuddy') >= 0 && (tx0.indexOf('账号') >= 0 || tx0.indexOf('Account') >= 0);
    if (!inEditor) {
      var old = document.querySelector('.ago-wb');
      if (old) old.remove();
      return;
    }
    if (document.querySelector('.ago-wb')) return;
    var app = document.querySelector('#app') || document.body;
    var txt = (app.textContent || '');
    if (txt.indexOf('WorkBuddy') < 0) return;

    var row = h('div', 'ago-wb');
    var line = h('div', 'ago-line');
    line.appendChild(h('span', 'ago-lbl', '自动签到'));
    var sw = h('button', 'ago-switch small');
    sw.appendChild(h('span', 'ago-knob'));
    var a = {};
    try { a = JSON.parse(H.autoCheckin()); } catch (e) {}
    if (a.on) sw.classList.add('on');
    sw.onclick = function () {
      var on = !sw.classList.contains('on');
      sw.classList.toggle('on', on);
      H.setAutoCheckin(on);
      say(on ? '已开启每日自动签到（09:05）' : '已关闭');
    };
    var manual = h('button', 'ago-act', '手动签到');
    var said = h('div', 'ago-wbsaid');
    manual.onclick = function () {
      said.textContent = '签到中…';
      H.execAsync(JSON.stringify(['accounts', 'checkin']));
      var t = setInterval(function () {
        if (H.taskRunning()) return;
        clearInterval(t);
        said.textContent = (H.taskOutput() || '').split('\n').slice(-2).join(' ').slice(0, 160);
      }, 900);
    };
    line.append(sw, manual);

    var accs = h('div', 'ago-accs');
    function drawAcc() {
      var list = [];
      try { list = JSON.parse(H.wbAccounts()); } catch (e) {}
      var seen = {};
      accs.replaceChildren();
      if (!list.length) return;
      list.forEach(function (x, i) {
        var r = h('div', 'ago-accrow');
        var who = (x.who || '').trim();
        var label = who ? who : ('账号 ' + (i + 1));
        if (x.primary) label += '（主）';
        if (who && (seen[who] || 0) > 0) label = who + ' · ' + (x.key.split('#')[1] || (i + 1));
        seen[who] = (seen[who] || 0) + 1;
        r.appendChild(h('span', 'ago-acclbl', label));
        var up = h('button', 'ago-mini', '↑');
        up.disabled = i === 0;
        up.onclick = function () { H.wbMove(x.key, -1); setTimeout(drawAcc, 1600); };
        var dn = h('button', 'ago-mini', '↓');
        dn.disabled = i === list.length - 1;
        dn.onclick = function () { H.wbMove(x.key, 1); setTimeout(drawAcc, 1600); };
        var del = h('button', 'ago-mini danger', '删除');
        del.onclick = function () {
          if (!confirm('删除账号 ' + label + '？')) return;
          H.wbRemove(x.key);
          setTimeout(drawAcc, 1600);
        };
        r.append(up, dn, del);
        accs.appendChild(r);
      });
    }
    setTimeout(drawAcc, 500);

    row.append(line, said, accs);
    // inside the editor, right under its title block
    var head = null;
    var els = app.querySelectorAll('h1,h2,h3,b,div');
    for (var i = 0; i < els.length; i++) {
      if ((els[i].textContent || '').indexOf('WorkBuddy') === 0 && els[i].children.length < 6) { head = els[i]; break; }
    }
    if (head && head.parentNode) head.parentNode.insertBefore(row, head.nextSibling);
    else app.insertBefore(row, app.firstChild);
  }

  function cardCheckin() {
    var app = document.body;
    if (document.querySelector('.ago-cardcheck')) return;
    var all = app.querySelectorAll('*');
    var best = null;
    for (var i = 0; i < all.length; i++) {
      var n = all[i];
      var t = n.textContent || '';
      if (t.indexOf('WorkBuddy') >= 0 && t.indexOf('已登录') >= 0 && t.length < 400) {
        if (!best || best.contains(n)) best = n;
      }
    }
    if (!best) return;
    var row = h('div', 'ago-cardcheck');
    row.appendChild(h('span', 'ago-lbl', '自动签到'));
    var sw = h('button', 'ago-switch small');
    sw.appendChild(h('span', 'ago-knob'));
    var a = {};
    try { a = JSON.parse(H.autoCheckin()); } catch (e) {}
    if (a.on) sw.classList.add('on');
    sw.onclick = function () {
      var on = !sw.classList.contains('on');
      sw.classList.toggle('on', on);
      H.setAutoCheckin(on);
      say(on ? '已开启：每天 09:05 自动签到' : '已关闭自动签到');
    };
    row.appendChild(sw);
    var cardEl = best;
    while (cardEl && cardEl !== document.body && (cardEl.textContent || '').indexOf('Free plan') < 0) cardEl = cardEl.parentElement;
    if (!cardEl || cardEl === document.body) cardEl = best.parentElement || best;
    // place it as a sibling right under the whole card, not inside its title row
    if (cardEl.parentElement) cardEl.parentElement.insertBefore(row, cardEl.nextSibling);
    else cardEl.appendChild(row);
  }

  function dropSponsors() {
    var all = document.querySelectorAll('div,section,h2,h3,span');
    var hdr = null;
    for (var i = 0; i < all.length; i++) {
      var t = (all[i].textContent || '').trim();
      if (t.indexOf('合作伙伴') === 0 && t.length < 60 && !all[i].querySelector('a,button')) { hdr = all[i]; break; }
      if (t.indexOf('合作伙伴') === 0 && t.length < 60) { hdr = all[i]; break; }
    }
    if (!hdr) return;
    var p = hdr.parentElement;
    if (!p) return;
    hdr.style.display = 'none';
    var sibs = Array.prototype.slice.call(p.children);
    var at = sibs.indexOf(hdr);
    for (var j = at + 1; j < sibs.length; j++) {
      var s = sibs[j];
      var tx = s.textContent || '';
      if (tx.indexOf('订阅') >= 0 || tx.indexOf('模型厂商') >= 0) break;
      s.style.display = 'none';
    }
  }

  var ADS = [
    '一份订阅，所有 agent 共享 K3 额度',
    '企业级 AI 中转，直连全球主流模型',
    '企业级 AI 网关，畅连全球大模型',
    '高缓存满血 Claude，可开票，注册送 $3',
    '满血 Claude 低至 1.5 折，注册送 $3',
    '低至 0.6 折，Claude/GPT/DeepSeek 全有'
  ];
  function dropAds() {
    var all = document.querySelectorAll('div,a,li,button,span');
    for (var i = 0; i < all.length; i++) {
      var n = all[i];
      var t = n.textContent || '';
      if (t.length > 200) continue;
      for (var k = 0; k < ADS.length; k++) {
        if (t.indexOf(ADS[k]) >= 0) {
          var row = n;
          var guard = 0;
          while (row.parentElement && (row.parentElement.textContent || '').length < 200 && guard++ < 4) row = row.parentElement;
          row.style.display = 'none';
          break;
        }
      }
    }
  }

  function dropMore() {
    var all = document.querySelectorAll('button, a, div, span');
    for (var i = 0; i < all.length; i++) {
      var t = (all[i].textContent || '').trim();
      if (t === '更多' || t === 'More') {
        var b = all[i].closest('button') || all[i];
        b.style.display = 'none';
      }
    }
  }

  var WORDS = [
    ['在电脑上', '在手机上'], ['桌面版', '手机版'], ['电脑上', '手机上'],
    ['浏览器', 'App'], ['菜单栏', '顶部栏'], ['系统托盘', '通知栏'],
    ['右键', '长按'], ['鼠标', '手指'], ['文件夹', '目录'], ['窗口', '页面']
  ];
  function phoneWords() {
    var app = document.querySelector('#app');
    if (!app) return;
    var walker = document.createTreeWalker(app, NodeFilter.SHOW_TEXT, null);
    var n;
    while ((n = walker.nextNode())) {
      var s = n.nodeValue;
      if (!s || s.length > 200) continue;
      var out = s;
      for (var i = 0; i < WORDS.length; i++) out = out.split(WORDS[i][0]).join(WORDS[i][1]);
      if (out !== s) n.nodeValue = out;
    }
  }

  function mount() {
    var top = document.querySelector('header.top');
    if (top) top.appendChild(box); else document.body.appendChild(box);
    document.body.appendChild(page);
    function fit() {
      var hd = document.querySelector('header.top'), nv = document.getElementById('nav');
      page.style.top = Math.round(hd ? hd.getBoundingClientRect().bottom : 56) + 'px';
      page.style.bottom = Math.round(nv ? Math.max(0, window.innerHeight - nv.getBoundingClientRect().top) : 0) + 'px';
    }
    fit(); window.addEventListener('resize', fit);
    var mo = new MutationObserver(function () { cardCheckin(); dropMore(); dropSponsors(); dropAds(); dropAds(); });
    mo.observe(document.body, { childList: true, subtree: true });
    cardCheckin(); dropMore(); dropSponsors();
    setInterval(function () { paint(); fit(); phoneWords(); }, 1500);
    read(); paint();
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mount); else mount();
})();