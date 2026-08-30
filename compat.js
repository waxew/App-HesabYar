/*
 * تکمیل قابلیت‌های Android در نسخه Web حسابیار.
 * این لایه بعد از app.js و parity.js اجرا می‌شود و قابلیت‌های پیشرفته‌تر را جایگزین/تکمیل می‌کند.
 */

/* ------------------------ محاسبات تکمیلی فروشنده ------------------------ */

/* بیشترین تخفیف امن بدون افتادن زیر Break-even. */
function maxSafeDiscountWeb(currentPrice, breakEvenPrice) {
  if (currentPrice <= 0 || breakEvenPrice < 0) return null;
  if (breakEvenPrice >= currentPrice) return 0;
  return Math.min(100, Math.max(0, (1 - breakEvenPrice / currentPrice) * 100));
}

/* قیمت عمده هرگز زیر Break-even پیشنهاد نمی‌شود. */
function bulkPriceWeb(retailUnitPrice, breakEvenUnitPrice, quantity, discountPercent) {
  if (retailUnitPrice < 0 || breakEvenUnitPrice < 0 || quantity <= 0 || discountPercent < 0 || discountPercent > 100) return null;
  const requested = retailUnitPrice * (1 - discountPercent / 100);
  const unitPrice = Math.max(requested, breakEvenUnitPrice);
  return { unitPrice, totalPrice: unitPrice * quantity, unitProfit: unitPrice - breakEvenUnitPrice };
}

/* نسخه کامل‌تر دستیار فروشنده مطابق AdvancedScreens.kt. */
renderSeller = function renderSellerCompat() {
  root.innerHTML = `
    <section class="panel"><div class="panel-head"><div><h1>دستیار فروشنده</h1><p>قیمت‌گذاری، تخفیف امن، سناریو و فروش عمده.</p></div></div><div class="panel-body">
      <div class="form-grid">
        ${numberField('sPurchase', `قیمت خرید (${state.currency})`)}
        ${numberField('sShipping', `ارسال ورودی (${state.currency})`, '0')}
        ${numberField('sPackaging', `بسته‌بندی (${state.currency})`, '0')}
        ${numberField('sOther', `سایر هزینه ثابت (${state.currency})`, '0')}
        ${numberField('sAdvertising', 'تبلیغات (%)', '0')}
        ${numberField('sPlatform', 'کارمزد پلتفرم (%)', '0')}
        ${numberField('sGateway', 'کارمزد درگاه (%)', '0')}
        ${numberField('sTax', 'مالیات/عوارض (%)', '0')}
        ${numberField('sMargin', 'حاشیه سود هدف (%)', '25')}
      </div>
      <div class="result-actions"><button id="sellerCalculate" class="primary-button" type="button">محاسبه قیمت فروش</button></div>
      <div id="sellerResult"></div>
      <div id="sellerAdvanced" class="is-hidden" style="margin-top:16px">
        <div class="section-heading"><div><h2>تخفیف امن و عمده</h2><p>قیمت فعلی و شرایط فروش عمده را بررسی کن.</p></div></div>
        <div class="form-grid">
          ${numberField('sCurrent', `قیمت فعلی (${state.currency})`, '')}
          ${numberField('sBulkQty', 'تعداد عمده', '10')}
          ${numberField('sBulkDiscount', 'تخفیف عمده (%)', '10')}
        </div>
        <div class="result-actions"><button id="sellerAdvancedCalc" class="secondary-button" type="button">محاسبه تخفیف و عمده</button></div>
        <div id="sellerAdvancedResult"></div>
      </div>
    </div></section>`;

  let lastResult = null;
  let lastValues = null;

  document.getElementById('sellerCalculate').addEventListener('click', () => {
    const values = {
      purchase: toNumber(document.getElementById('sPurchase').value) ?? -1,
      shipping: toNumber(document.getElementById('sShipping').value) ?? 0,
      packaging: toNumber(document.getElementById('sPackaging').value) ?? 0,
      other: toNumber(document.getElementById('sOther').value) ?? 0,
      advertising: toNumber(document.getElementById('sAdvertising').value) ?? 0,
      platform: toNumber(document.getElementById('sPlatform').value) ?? 0,
      gateway: toNumber(document.getElementById('sGateway').value) ?? 0,
      tax: toNumber(document.getElementById('sTax').value) ?? 0,
      margin: toNumber(document.getElementById('sMargin').value) ?? -1
    };
    const result = sellerPricing(values);
    const box = document.getElementById('sellerResult');
    if (!result) {
      box.innerHTML = '<div class="validation-message">مقادیر نامعتبرند یا مجموع کارمزدها و Margin به ۱۰۰٪ رسیده است.</div>';
      document.getElementById('sellerAdvanced').classList.add('is-hidden');
      return;
    }
    lastResult = result;
    lastValues = values;
    const scenarios = pricingScenarios(values);
    renderResult(box, {
      label: 'قیمت فروش پیشنهادی',
      value: `${money(result.suggestedPrice)} ${state.currency}`,
      rows: [
        ['هزینه ثابت واقعی', `${money(result.fixedCost)} ${state.currency}`],
        ['هزینه‌های درصدی', percent(result.variableRatePercent)],
        ['سود خالص مورد انتظار', `${money(result.expectedProfit)} ${state.currency}`],
        ['Margin', percent(result.expectedMarginPercent)],
        ['قیمت سربه‌سر', `${money(result.breakEvenPrice)} ${state.currency}`]
      ],
      save: () => {
        saveHistory('قیمت‌گذاری فروشنده', `هزینه ${money(result.fixedCost)} / Margin ${percent(result.expectedMarginPercent)}`, `${money(result.suggestedPrice)} ${state.currency}`);
        parityToast('در تاریخچه ذخیره شد.');
      }
    });
    box.insertAdjacentHTML('beforeend', `<div class="summary-grid">${scenarios.map(item => summaryCard(`${item.label} · ${item.margin}٪`, `${money(item.salePrice)} ${state.currency}`)).join('')}</div>`);
    document.getElementById('sCurrent').value = money(result.suggestedPrice);
    document.getElementById('sellerAdvanced').classList.remove('is-hidden');
  });

  document.getElementById('sellerAdvancedCalc').addEventListener('click', () => {
    if (!lastResult || !lastValues) return parityToast('اول قیمت فروش را محاسبه کن.');
    const current = toNumber(document.getElementById('sCurrent').value) ?? lastResult.suggestedPrice;
    const qty = toNumber(document.getElementById('sBulkQty').value) ?? 0;
    const discount = toNumber(document.getElementById('sBulkDiscount').value) ?? -1;
    const safe = maxSafeDiscountWeb(current, lastResult.breakEvenPrice);
    const bulk = bulkPriceWeb(lastResult.suggestedPrice, lastResult.breakEvenPrice, qty, discount);
    const box = document.getElementById('sellerAdvancedResult');
    if (safe == null || bulk == null) {
      box.innerHTML = '<div class="validation-message">اطلاعات تخفیف یا عمده معتبر نیست.</div>';
      return;
    }
    box.innerHTML = `
      <div class="summary-grid">
        ${summaryCard('حداکثر تخفیف امن', percent(safe))}
        ${summaryCard('قیمت هر عدد عمده', `${money(bulk.unitPrice)} ${state.currency}`)}
        ${summaryCard('جمع سفارش عمده', `${money(bulk.totalPrice)} ${state.currency}`)}
      </div>
      <div class="notice ${bulk.unitPrice === lastResult.breakEvenPrice ? 'warning' : 'success'}">سود هر واحد نسبت به Break-even: <strong>${money(bulk.unitProfit)} ${state.currency}</strong></div>`;
  });
};

/* ------------------------ Marketplace با قانون پلکانی ------------------- */

renderMarketplace = function renderMarketplaceCompat() {
  proShell('مقایسه مارکت‌پلیس', 'کارمزد ثابت، درصدی و پلکانی و سود خالص', `
    <div class="form-grid">
      ${numberField('mpCost', 'هزینه تمام‌شده')}
      ${numberField('mpSale', 'قیمت فروش')}
      <div class="field"><label for="mpName">نام کانال</label><input id="mpName" class="input" dir="rtl" style="text-align:right"></div>
      ${numberField('mpFee', 'کارمزد درصدی', '0')}
      ${numberField('mpFixed', 'کارمزد ثابت', '0')}
      ${numberField('mpThreshold', 'آستانه کارمزد پلکانی', '0')}
    </div>
    <div class="result-actions"><button id="mpCompare" class="primary-button">مقایسه</button><button id="mpAdd" class="secondary-button">افزودن پروفایل</button></div>
    <div id="mpResult"></div><div id="mpList" class="data-list"></div>`);

  /* پروفایل‌های قدیمی بدون threshold هم سازگار باقی می‌مانند. */
  const drawProfiles = () => {
    document.getElementById('mpList').innerHTML = parityState.marketplaces.map(profile => `
      <div class="data-item"><div class="data-item-main"><strong>${escapeHtml(profile.name)}</strong>
      <span>${clean(profile.feePercent || 0)}٪ + ${money(profile.fixedFee || 0)} ثابت${profile.threshold > 0 ? ` · از ${money(profile.threshold)} به بالا` : ''}</span></div>
      <button class="small-button danger" data-del-mp="${profile.id}">حذف</button></div>`).join('');
    document.querySelectorAll('[data-del-mp]').forEach(button => button.addEventListener('click', () => {
      parityState.marketplaces = parityState.marketplaces.filter(profile => String(profile.id) !== button.dataset.delMp);
      paritySave(PARITY_KEYS.marketplaces, parityState.marketplaces);
      drawProfiles();
    }));
  };

  document.getElementById('mpAdd').addEventListener('click', () => {
    const name = document.getElementById('mpName').value.trim();
    const feePercent = toNumber(document.getElementById('mpFee').value) ?? 0;
    const fixedFee = toNumber(document.getElementById('mpFixed').value) ?? 0;
    const threshold = toNumber(document.getElementById('mpThreshold').value) ?? 0;
    if (!name || feePercent < 0 || fixedFee < 0 || threshold < 0) return parityToast('پروفایل نامعتبر است.');
    parityState.marketplaces.push({ id: Date.now(), name, feePercent, fixedFee, threshold });
    paritySave(PARITY_KEYS.marketplaces, parityState.marketplaces);
    drawProfiles();
  });

  document.getElementById('mpCompare').addEventListener('click', () => {
    const cost = toNumber(document.getElementById('mpCost').value);
    const sale = toNumber(document.getElementById('mpSale').value);
    if (cost == null || sale == null || cost < 0 || sale <= 0) return;
    const quotes = parityState.marketplaces.map(profile => {
      const percentFee = profile.threshold > 0 && sale < profile.threshold ? 0 : sale * (profile.feePercent || 0) / 100;
      const fees = percentFee + (profile.fixedFee || 0);
      const profit = sale - cost - fees;
      return { ...profile, fees, profit, margin: profit / sale * 100 };
    }).sort((a, b) => b.profit - a.profit);
    document.getElementById('mpResult').innerHTML = quotes.length ? `
      <div class="result-card"><span class="result-label">بهترین کانال</span><strong class="result-value" style="direction:rtl">${escapeHtml(quotes[0].name)}</strong>
      ${quotes.map(q => `<div class="result-row"><span>${escapeHtml(q.name)} · کارمزد ${money(q.fees)}</span><strong>${money(q.profit)} · ${percent(q.margin)}</strong></div>`).join('')}</div>` : '';
  });
  drawProfiles();
};

/* -------------------- تحلیل قیمت: تورم و Shrinkflation ----------------- */

function personalInflationWeb(oldBasket, newBasket) {
  if (oldBasket <= 0 || newBasket < 0) return null;
  const inflation = (newBasket - oldBasket) / oldBasket * 100;
  const purchasingPower = newBasket > 0 ? oldBasket / newBasket * 100 - 100 : 0;
  return { inflation, purchasingPower };
}

function shrinkflationWeb(oldPrice, oldQty, newPrice, newQty) {
  if (oldPrice < 0 || newPrice < 0 || oldQty <= 0 || newQty <= 0) return null;
  const oldUnit = oldPrice / oldQty;
  const newUnit = newPrice / newQty;
  const unitChange = oldUnit > 0 ? (newUnit - oldUnit) / oldUnit * 100 : 0;
  const qtyDrop = (oldQty - newQty) / oldQty * 100;
  return { unitChange, qtyDrop, isShrinkflation: qtyDrop > 0 && unitChange > 0 };
}

renderAnalytics = function renderAnalyticsCompat() {
  const records = parityState.priceBook.filter(item => item.quantity > 0);
  const byName = {};
  records.forEach(item => { (byName[item.name] ||= []).push(item); });
  Object.values(byName).forEach(items => items.sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0)));
  const firstSum = Object.values(byName).reduce((sum, items) => sum + (items[0]?.price / items[0]?.quantity || 0), 0);
  const lastSum = Object.values(byName).reduce((sum, items) => sum + (items.at(-1)?.price / items.at(-1)?.quantity || 0), 0);
  const inflation = personalInflationWeb(firstSum, lastSum);
  const cheapest = records.slice().sort((a, b) => a.price / a.quantity - b.price / b.quantity)[0];

  proShell('تحلیل قیمت', 'بهترین قیمت، تورم شخصی، Shrinkflation و افت Margin', `
    ${cheapest ? `<div class="result-card"><span class="result-label">بهترین قیمت واحد</span><strong class="result-value" style="direction:rtl">${escapeHtml(cheapest.name)}</strong><div class="result-row"><span>قیمت هر ${escapeHtml(cheapest.unit)}</span><strong>${money(cheapest.price / cheapest.quantity)} ${state.currency}</strong></div></div>` : '<div class="empty-state">برای تحلیل، ابتدا در دفترچه قیمت داده ثبت کن.</div>'}
    ${inflation ? `<div class="summary-grid">${summaryCard('تورم شخصی سبد', percent(inflation.inflation))}${summaryCard('تغییر قدرت خرید', percent(inflation.purchasingPower))}</div>` : ''}
    <div class="form-grid">
      <div class="field"><label for="anProduct">محصول</label><select id="anProduct" class="select"><option value="">انتخاب محصول</option>${Object.keys(byName).map(name => `<option value="${escapeHtml(name)}">${escapeHtml(name)}</option>`).join('')}</select></div>
      ${numberField('anSale', 'قیمت فروش ثابت', '')}
      ${numberField('anFee', 'کارمزد (%)', '0')}
    </div>
    <div class="result-actions"><button id="anAnalyze" class="primary-button">تحلیل محصول</button></div>
    <div id="anResult"></div>
    <div class="data-list">${Object.entries(byName).map(([name, items]) => {
      const first = items[0]; const last = items.at(-1); const change = first?.price > 0 ? (last.price - first.price) / first.price * 100 : 0;
      return `<div class="data-item"><div class="data-item-main"><strong>${escapeHtml(name)}</strong><span>${items.length} ثبت · تغییر قیمت کل ${percent(change)}</span></div></div>`;
    }).join('')}</div>`);

  document.getElementById('anAnalyze').addEventListener('click', () => {
    const name = document.getElementById('anProduct').value;
    const items = byName[name] || [];
    const box = document.getElementById('anResult');
    if (items.length < 2) { box.innerHTML = '<div class="notice">برای تحلیل تغییرات حداقل دو ثبت از یک محصول لازم است.</div>'; return; }
    const oldItem = items.at(-2), newItem = items.at(-1);
    const shrink = shrinkflationWeb(oldItem.price, oldItem.quantity, newItem.price, newItem.quantity);
    const sale = toNumber(document.getElementById('anSale').value);
    const fee = toNumber(document.getElementById('anFee').value) ?? 0;
    let marginHtml = '';
    if (sale != null && sale > 0 && fee >= 0 && fee < 100) {
      const oldMargin = (sale - oldItem.price - sale * fee / 100) / sale * 100;
      const newMargin = (sale - newItem.price - sale * fee / 100) / sale * 100;
      const diff = newMargin - oldMargin;
      marginHtml = `<div class="notice ${diff < -3 ? 'warning' : 'success'}">تغییر Margin: <strong>${percent(diff)}</strong>${diff < -3 ? ' · هشدار: افت بیش از ۳ واحد درصد' : ''}</div>`;
    }
    box.innerHTML = `
      ${shrink ? `<div class="summary-grid">${summaryCard('تغییر قیمت واحد', percent(shrink.unitChange))}${summaryCard('کاهش مقدار بسته', percent(shrink.qtyDrop))}${summaryCard('Shrinkflation', shrink.isShrinkflation ? 'تشخیص داده شد' : 'خیر')}</div>` : ''}
      ${marginHtml}`;
  });
};

/* -------------------------- What-if Slider ------------------------------ */

renderWhatIf = function renderWhatIfCompat() {
  proShell('شبیه‌ساز What-if', 'اثر زنده تخفیف روی قیمت مشتری، سود و Margin', `
    <div class="form-grid">${numberField('wSale', 'قیمت فروش پایه')}${numberField('wCost', 'هزینه تمام‌شده')}${numberField('wFee', 'کارمزد (%)', '0')}</div>
    <div class="range-field"><label for="wRange"><span>تخفیف</span><strong id="wRangeLabel">10٪</strong></label><input id="wRange" type="range" min="0" max="100" step="1" value="10"></div>
    <div id="wResult" style="margin-top:14px"></div>`);

  const recalc = () => {
    const discount = Number(document.getElementById('wRange').value);
    document.getElementById('wRangeLabel').textContent = `${discount}٪`;
    const result = whatIfResult(toNumber(document.getElementById('wSale').value) ?? -1, toNumber(document.getElementById('wCost').value) ?? -1, toNumber(document.getElementById('wFee').value) ?? 0, discount);
    const box = document.getElementById('wResult');
    if (!result) { box.innerHTML = ''; return; }
    renderResult(box, { label: result.isLoss ? 'زیان پس از تخفیف' : 'سود پس از تخفیف', value: `${money(Math.abs(result.profit))} ${state.currency}`, negative: result.isLoss, rows: [['قیمت مشتری', `${money(result.customerPrice)} ${state.currency}`], ['Margin', percent(result.margin)]] });
  };
  ['wSale', 'wCost', 'wFee', 'wRange'].forEach(id => document.getElementById(id).addEventListener('input', recalc));
};

/* ---------------------------- XLSX واقعی -------------------------------- */

/* Escape XML برای فایل Excel. */
function xmlEscape(value) {
  return String(value ?? '').replace(/[&<>"']/g, ch => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&apos;' }[ch]));
}

/* CRC32 برای ساخت ZIP بدون کتابخانه خارجی. */
const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = (c & 1) ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c >>> 0;
  }
  return table;
})();

function crc32(bytes) {
  let c = 0xffffffff;
  for (const byte of bytes) c = CRC_TABLE[(c ^ byte) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function concatBytes(parts) {
  const length = parts.reduce((sum, part) => sum + part.length, 0);
  const out = new Uint8Array(length);
  let offset = 0;
  parts.forEach(part => { out.set(part, offset); offset += part.length; });
  return out;
}

function makeZipStore(entries) {
  const encoder = new TextEncoder();
  const locals = [];
  const centrals = [];
  let offset = 0;

  entries.forEach(entry => {
    const name = encoder.encode(entry.name);
    const data = entry.data instanceof Uint8Array ? entry.data : encoder.encode(entry.data);
    const crc = crc32(data);
    const local = new Uint8Array(30 + name.length);
    const lv = new DataView(local.buffer);
    lv.setUint32(0, 0x04034b50, true);
    lv.setUint16(4, 20, true);
    lv.setUint16(6, 0, true);
    lv.setUint16(8, 0, true);
    lv.setUint16(10, 0, true);
    lv.setUint16(12, 0, true);
    lv.setUint32(14, crc, true);
    lv.setUint32(18, data.length, true);
    lv.setUint32(22, data.length, true);
    lv.setUint16(26, name.length, true);
    lv.setUint16(28, 0, true);
    local.set(name, 30);
    locals.push(local, data);

    const central = new Uint8Array(46 + name.length);
    const cv = new DataView(central.buffer);
    cv.setUint32(0, 0x02014b50, true);
    cv.setUint16(4, 20, true);
    cv.setUint16(6, 20, true);
    cv.setUint16(8, 0, true);
    cv.setUint16(10, 0, true);
    cv.setUint16(12, 0, true);
    cv.setUint16(14, 0, true);
    cv.setUint32(16, crc, true);
    cv.setUint32(20, data.length, true);
    cv.setUint32(24, data.length, true);
    cv.setUint16(28, name.length, true);
    cv.setUint16(30, 0, true);
    cv.setUint16(32, 0, true);
    cv.setUint16(34, 0, true);
    cv.setUint16(36, 0, true);
    cv.setUint32(38, 0, true);
    cv.setUint32(42, offset, true);
    central.set(name, 46);
    centrals.push(central);
    offset += local.length + data.length;
  });

  const centralBytes = concatBytes(centrals);
  const end = new Uint8Array(22);
  const ev = new DataView(end.buffer);
  ev.setUint32(0, 0x06054b50, true);
  ev.setUint16(4, 0, true); ev.setUint16(6, 0, true);
  ev.setUint16(8, entries.length, true); ev.setUint16(10, entries.length, true);
  ev.setUint32(12, centralBytes.length, true); ev.setUint32(16, offset, true); ev.setUint16(20, 0, true);
  return concatBytes([...locals, centralBytes, end]);
}

function cellRef(col, row) {
  let n = col + 1, letters = '';
  while (n) { const r = (n - 1) % 26; letters = String.fromCharCode(65 + r) + letters; n = Math.floor((n - 1) / 26); }
  return `${letters}${row + 1}`;
}

function createHistoryXlsx() {
  const rows = [['زمان','عنوان','جزئیات','نتیجه'], ...state.history.map(item => [item.createdAt || '', item.title || '', item.details || '', item.result || ''])];
  const sheetRows = rows.map((row, r) => `<row r="${r + 1}">${row.map((value, c) => `<c r="${cellRef(c, r)}" t="inlineStr"><is><t>${xmlEscape(value)}</t></is></c>`).join('')}</row>`).join('');
  const sheet = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>${sheetRows}</sheetData></worksheet>`;
  const entries = [
    { name: '[Content_Types].xml', data: `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>` },
    { name: '_rels/.rels', data: `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>` },
    { name: 'xl/workbook.xml', data: `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="History" sheetId="1" r:id="rId1"/></sheets></workbook>` },
    { name: 'xl/_rels/workbook.xml.rels', data: `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>` },
    { name: 'xl/worksheets/sheet1.xml', data: sheet }
  ];
  return makeZipStore(entries);
}

function downloadBlob(filename, bytes, type) {
  const blob = new Blob([bytes], { type });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = filename; document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/* ZIP/XLSX reader حداقلی برای فایل‌های متداول Excel. */
async function unzipEntryMap(buffer) {
  const bytes = new Uint8Array(buffer);
  const view = new DataView(buffer);
  let eocd = -1;
  for (let i = bytes.length - 22; i >= Math.max(0, bytes.length - 65557); i--) {
    if (view.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('ZIP EOCD not found');
  const count = view.getUint16(eocd + 10, true);
  let pos = view.getUint32(eocd + 16, true);
  const map = new Map();
  const decoder = new TextDecoder();
  for (let i = 0; i < count; i++) {
    if (view.getUint32(pos, true) !== 0x02014b50) throw new Error('ZIP central header invalid');
    const method = view.getUint16(pos + 10, true);
    const compressedSize = view.getUint32(pos + 20, true);
    const nameLen = view.getUint16(pos + 28, true);
    const extraLen = view.getUint16(pos + 30, true);
    const commentLen = view.getUint16(pos + 32, true);
    const localOffset = view.getUint32(pos + 42, true);
    const name = decoder.decode(bytes.slice(pos + 46, pos + 46 + nameLen));
    const localNameLen = view.getUint16(localOffset + 26, true);
    const localExtraLen = view.getUint16(localOffset + 28, true);
    const dataStart = localOffset + 30 + localNameLen + localExtraLen;
    const compressed = bytes.slice(dataStart, dataStart + compressedSize);
    let data;
    if (method === 0) data = compressed;
    else if (method === 8 && 'DecompressionStream' in window) {
      const stream = new Blob([compressed]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
      data = new Uint8Array(await new Response(stream).arrayBuffer());
    } else throw new Error('ZIP compression unsupported');
    map.set(name, data);
    pos += 46 + nameLen + extraLen + commentLen;
  }
  return map;
}

async function readFirstSheetXlsx(file) {
  const entries = await unzipEntryMap(await file.arrayBuffer());
  const decoder = new TextDecoder();
  const parser = new DOMParser();
  let shared = [];
  if (entries.has('xl/sharedStrings.xml')) {
    const doc = parser.parseFromString(decoder.decode(entries.get('xl/sharedStrings.xml')), 'application/xml');
    shared = [...doc.getElementsByTagName('si')].map(si => [...si.getElementsByTagName('t')].map(t => t.textContent || '').join(''));
  }
  const sheetBytes = entries.get('xl/worksheets/sheet1.xml');
  if (!sheetBytes) throw new Error('sheet1 missing');
  const sheet = parser.parseFromString(decoder.decode(sheetBytes), 'application/xml');
  const rows = [...sheet.getElementsByTagName('row')].map(row => [...row.getElementsByTagName('c')].map(cell => {
    const type = cell.getAttribute('t');
    if (type === 'inlineStr') return cell.getElementsByTagName('t')[0]?.textContent || '';
    const value = cell.getElementsByTagName('v')[0]?.textContent || '';
    return type === 's' ? shared[Number(value)] ?? '' : value;
  }));
  return rows;
}

/* Import ابزار حرفه‌ای را به CSV + XLSX ارتقا می‌دهد. */
renderImport = function renderImportCompat() {
  proShell('Import محصولات', 'CSV و Excel (.xlsx) به دفترچه قیمت', `
    <div class="capability-note">ستون‌های پیشنهادی: نام/Name، قیمت/Price، مقدار/Quantity، واحد/Unit. اولین Sheet فایل Excel خوانده می‌شود.</div>
    <div class="field" style="margin-top:12px"><label for="importFile">فایل CSV یا XLSX</label><input id="importFile" class="input" type="file" accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" style="padding:10px;direction:rtl;text-align:right"></div>
    <div id="importResult"></div>`);

  document.getElementById('importFile').addEventListener('change', async event => {
    const file = event.target.files?.[0]; if (!file) return;
    try {
      let rows;
      if (file.name.toLowerCase().endsWith('.xlsx')) rows = await readFirstSheetXlsx(file);
      else rows = (await file.text()).split(/\r?\n/).filter(Boolean).map(line => line.split(',').map(v => v.trim().replace(/^"|"$/g, '')));
      if (!rows.length) throw new Error('empty');
      const headers = rows[0].map(h => String(h).trim().toLowerCase());
      const idx = names => headers.findIndex(h => names.some(name => h.includes(name)));
      const nameIndex = idx(['نام','name','product']);
      const priceIndex = idx(['قیمت','price']);
      const qtyIndex = idx(['مقدار','تعداد','quantity','qty']);
      const unitIndex = idx(['واحد','unit']);
      const start = nameIndex >= 0 && priceIndex >= 0 ? 1 : 0;
      let added = 0;
      for (let r = start; r < rows.length; r++) {
        const row = rows[r];
        const name = String(row[nameIndex >= 0 ? nameIndex : 0] ?? '').trim();
        const price = toNumber(row[priceIndex >= 0 ? priceIndex : 1]);
        const quantity = toNumber(row[qtyIndex >= 0 ? qtyIndex : 2]) ?? 1;
        const unit = String(row[unitIndex >= 0 ? unitIndex : 3] ?? 'عدد').trim() || 'عدد';
        if (name && price != null && price >= 0 && quantity > 0) {
          parityState.priceBook.push({ id: Date.now() + Math.random(), name, price, quantity, unit, createdAt: Date.now() });
          added++;
        }
      }
      paritySave(PARITY_KEYS.priceBook, parityState.priceBook);
      document.getElementById('importResult').innerHTML = `<div class="result-card"><span class="result-label">Import انجام شد</span><strong class="result-value">${added} ردیف</strong></div>`;
    } catch (error) {
      document.getElementById('importResult').innerHTML = `<div class="validation-message">خواندن فایل ممکن نبود. فایل XLSX باید معمولی و Sheet اول شامل داده باشد.</div>`;
      console.error(error);
    }
  });
};

/* Data Tools: افزودن XLSX واقعی و چاپ/PDF. */
renderDataTools = function renderDataToolsCompat() {
  root.innerHTML = `
    <section class="panel"><div class="panel-head"><div><h1>خروجی و پشتیبان‌گیری</h1><p>CSV، Excel، Print/PDF و Backup/Restore.</p></div></div><div class="panel-body settings-list">
      <div class="setting-row"><div><strong>CSV تاریخچه</strong><span>سازگار با Excel و نرم‌افزارهای جدول.</span></div><button id="exportCsv" class="secondary-button" type="button">CSV</button></div>
      <div class="setting-row"><div><strong>Excel .xlsx</strong><span>فایل XLSX واقعی بدون کتابخانه خارجی.</span></div><button id="exportXlsx" class="secondary-button" type="button">XLSX</button></div>
      <div class="setting-row"><div><strong>PDF / چاپ</strong><span>گزارش تاریخچه از پنجره Print مرورگر؛ گزینه Save as PDF قابل انتخاب است.</span></div><button id="printHistory" class="secondary-button" type="button">چاپ / PDF</button></div>
      <div class="setting-row"><div><strong>Backup کامل</strong><span>تنظیمات، تاریخچه، سبد خرید، Price Book و داده‌های حرفه‌ای.</span></div><button id="exportBackup" class="primary-button" type="button">Backup JSON</button></div>
      <div class="setting-row"><div><strong>Restore</strong><span>بازیابی Backup استاندارد نسخه Web.</span></div><label class="secondary-button" style="display:grid;place-items:center;cursor:pointer"><input id="restoreBackup" type="file" accept="application/json,.json" hidden>انتخاب Backup</label></div>
    </div></section>`;

  document.getElementById('exportCsv').addEventListener('click', () => {
    const rows = [['زمان','عنوان','جزئیات','نتیجه'], ...state.history.map(item => [item.createdAt,item.title,item.details,item.result])];
    downloadText('HesabYar-history.csv', '\ufeff' + rows.map(row => row.map(csvCell).join(',')).join('\n'), 'text/csv;charset=utf-8');
  });
  document.getElementById('exportXlsx').addEventListener('click', () => {
    downloadBlob('HesabYar-history.xlsx', createHistoryXlsx(), 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
  });
  document.getElementById('printHistory').addEventListener('click', () => {
    const old = root.innerHTML;
    root.innerHTML = `<section class="print-invoice"><h2>گزارش تاریخچه حسابیار</h2><div class="invoice-meta">${new Date().toLocaleString('fa-IR')}</div><table><thead><tr><th>عنوان</th><th>جزئیات</th><th>نتیجه</th></tr></thead><tbody>${state.history.map(item => `<tr><td>${escapeHtml(item.title)}</td><td>${escapeHtml(item.details)}</td><td class="num">${escapeHtml(item.result)}</td></tr>`).join('')}</tbody></table></section>`;
    window.print();
    setTimeout(() => { root.innerHTML = old; renderDataToolsCompat(); }, 150);
  });
  document.getElementById('exportBackup').addEventListener('click', () => downloadText('HesabYar-backup.json', JSON.stringify(buildBackupObject(), null, 2), 'application/json'));
  document.getElementById('restoreBackup').addEventListener('change', event => restorePlainBackup(event.target.files?.[0]));
};

async function restorePlainBackup(file) {
  if (!file) return;
  try {
    const backup = JSON.parse(await file.text());
    if (backup.format !== 'HesabYar-Web-Backup') throw new Error('invalid');
    applyBackupObject(backup);
    parityToast('Backup بازیابی شد.');
  } catch { parityToast('فایل Backup معتبر نیست.'); }
}

function applyBackupObject(backup) {
  state.history = Array.isArray(backup.history) ? backup.history.slice(0, 100) : [];
  persistHistory();
  state.currency = backup.settings?.currency || state.currency;
  state.theme = backup.settings?.theme || state.theme;
  localStorage.setItem(STORAGE_KEYS.currency, state.currency);
  localStorage.setItem(STORAGE_KEYS.theme, state.theme);
  parityState.profileName = backup.settings?.profileName || parityState.profileName;
  parityState.profileImage = backup.settings?.profileImage || '';
  localStorage.setItem(PARITY_KEYS.profileName, parityState.profileName);
  localStorage.setItem(PARITY_KEYS.profileImage, parityState.profileImage);
  parityState.cart = Array.isArray(backup.cart) ? backup.cart : [];
  paritySave(PARITY_KEYS.cart, parityState.cart);
  parityState.budget = Number(backup.budget || 0);
  localStorage.setItem(PARITY_KEYS.budget, String(parityState.budget));
  parityState.priceBook = Array.isArray(backup.priceBook) ? backup.priceBook : [];
  paritySave(PARITY_KEYS.priceBook, parityState.priceBook);
  parityState.marketplaces = Array.isArray(backup.marketplaces) ? backup.marketplaces : [];
  paritySave(PARITY_KEYS.marketplaces, parityState.marketplaces);
  parityState.invoices = Array.isArray(backup.invoices) ? backup.invoices : [];
  paritySave(PARITY_KEYS.invoices, parityState.invoices);
  parityState.businesses = Array.isArray(backup.businesses) ? backup.businesses : [];
  paritySave(PARITY_KEYS.businesses, parityState.businesses);
  parityState.favorites = new Set(Array.isArray(backup.favorites) ? backup.favorites : []);
  paritySave(PARITY_KEYS.favorites, [...parityState.favorites]);
  applyTheme(); updateDrawerAvatar();
}

/* ------------------------ فاکتور + Print/PDF ---------------------------- */

renderInvoice = function renderInvoiceCompat() {
  const draft = [];
  proShell('فاکتور آفلاین', 'ثبت اقلام، تخفیف، مالیات و چاپ/PDF', `
    <div class="inline-form no-print"><div class="field"><label for="invTitle">شرح</label><input id="invTitle" class="input" dir="rtl" style="text-align:right"></div>${numberField('invQty','تعداد','1')}${numberField('invPrice','قیمت واحد')}<button id="invAdd" class="primary-button">افزودن</button></div>
    <div id="invLines" class="data-list no-print"></div>
    <div class="form-grid no-print" style="margin-top:14px">${numberField('invDiscount','تخفیف (%)','0')}${numberField('invTax','مالیات (%)','0')}</div>
    <div class="result-actions no-print"><button id="invCalc" class="primary-button">محاسبه</button><button id="invPrint" class="secondary-button" disabled>چاپ / ذخیره PDF</button></div>
    <div id="invResult"></div><div id="printInvoice"></div>`);

  const draw = () => {
    document.getElementById('invLines').innerHTML = draft.map((line, index) => `<div class="data-item"><div class="data-item-main"><strong>${escapeHtml(line.title)}</strong><span>${clean(line.quantity)} × ${money(line.unitPrice)} = ${money(line.quantity * line.unitPrice)}</span></div><button class="small-button danger" data-inv-del="${index}">حذف</button></div>`).join('');
    document.querySelectorAll('[data-inv-del]').forEach(button => button.addEventListener('click', () => { draft.splice(Number(button.dataset.invDel), 1); draw(); }));
  };

  document.getElementById('invAdd').addEventListener('click', () => {
    const title = document.getElementById('invTitle').value.trim();
    const quantity = toNumber(document.getElementById('invQty').value);
    const unitPrice = toNumber(document.getElementById('invPrice').value);
    if (!title || quantity == null || quantity <= 0 || unitPrice == null || unitPrice < 0) return;
    draft.push({ title, quantity, unitPrice }); draw();
  });

  document.getElementById('invCalc').addEventListener('click', () => {
    const totals = invoiceTotals(draft, toNumber(document.getElementById('invDiscount').value) ?? 0, toNumber(document.getElementById('invTax').value) ?? 0);
    if (!totals) return;
    renderResult(document.getElementById('invResult'), { label: 'جمع نهایی فاکتور', value: `${money(totals.finalTotal)} ${state.currency}`, rows: [['جمع اقلام', money(totals.subtotal)], ['تخفیف', money(totals.discountAmount)], ['مالیات', money(totals.taxAmount)]], save: () => {
      parityState.invoices.unshift({ id: Date.now(), createdAt: new Date().toISOString(), lines: draft.map(x => ({ ...x })), ...totals });
      paritySave(PARITY_KEYS.invoices, parityState.invoices); parityToast('فاکتور ذخیره شد.');
    }});
    document.getElementById('printInvoice').innerHTML = `
      <section class="print-invoice"><h2>فاکتور حسابیار</h2><div class="invoice-meta">${new Date().toLocaleString('fa-IR')}</div>
      <table><thead><tr><th>شرح</th><th>تعداد</th><th>قیمت واحد</th><th>جمع</th></tr></thead><tbody>${draft.map(line => `<tr><td>${escapeHtml(line.title)}</td><td class="num">${clean(line.quantity)}</td><td class="num">${money(line.unitPrice)}</td><td class="num">${money(line.quantity * line.unitPrice)}</td></tr>`).join('')}</tbody></table>
      <div class="invoice-summary"><div><span>جمع اقلام</span><strong>${money(totals.subtotal)}</strong></div><div><span>تخفیف</span><strong>${money(totals.discountAmount)}</strong></div><div><span>مالیات</span><strong>${money(totals.taxAmount)}</strong></div><div><span>مبلغ نهایی</span><strong>${money(totals.finalTotal)} ${state.currency}</strong></div></div></section>`;
    document.getElementById('invPrint').disabled = false;
  });
  document.getElementById('invPrint').addEventListener('click', () => window.print());
};

/* --------------------- Backup رمزدار + Restore -------------------------- */

async function decryptBackup(text, password) {
  const payload = JSON.parse(text);
  if (payload.format !== 'HesabYar-Web-Encrypted') throw new Error('format');
  const fromB64 = value => Uint8Array.from(atob(value), ch => ch.charCodeAt(0));
  const enc = new TextEncoder();
  const material = await crypto.subtle.importKey('raw', enc.encode(password), 'PBKDF2', false, ['deriveKey']);
  const key = await crypto.subtle.deriveKey({ name:'PBKDF2', salt:fromB64(payload.salt), iterations:120000, hash:'SHA-256' }, material, { name:'AES-GCM', length:256 }, false, ['decrypt']);
  const plain = await crypto.subtle.decrypt({ name:'AES-GCM', iv:fromB64(payload.iv) }, key, fromB64(payload.data));
  return new TextDecoder().decode(plain);
}

renderSecureBackup = function renderSecureBackupCompat() {
  proShell('Backup رمزدار', 'AES-256-GCM و Restore امن در خود مرورگر', `
    <div class="form-grid"><div class="field"><label for="securePass">رمز Backup</label><input id="securePass" class="input" type="password" autocomplete="new-password"></div><div class="field"><label for="secureRestorePass">رمز فایل برای Restore</label><input id="secureRestorePass" class="input" type="password" autocomplete="current-password"></div></div>
    <div class="result-actions"><button id="secureExport" class="primary-button">ساخت Backup رمزدار</button><label class="secondary-button" style="display:grid;place-items:center;cursor:pointer"><input id="secureRestoreFile" type="file" accept="application/json,.json" hidden>Restore رمزدار</label></div>
    <div class="capability-note">کل عملیات PBKDF2 + AES-256-GCM داخل مرورگر انجام می‌شود؛ رمز به سرور ارسال نمی‌شود.</div>`);

  document.getElementById('secureExport').addEventListener('click', async () => {
    const pass = document.getElementById('securePass').value;
    if (pass.length < 6) return parityToast('رمز حداقل ۶ کاراکتر باشد.');
    try { downloadText('HesabYar-secure-backup.json', await encryptBackup(JSON.stringify(buildBackupObject()), pass), 'application/json'); parityToast('Backup رمزدار ساخته شد.'); }
    catch { parityToast('Web Crypto در این مرورگر در دسترس نیست.'); }
  });
  document.getElementById('secureRestoreFile').addEventListener('change', async event => {
    const file = event.target.files?.[0];
    const pass = document.getElementById('secureRestorePass').value;
    if (!file || !pass) return parityToast('فایل و رمز را وارد کن.');
    try { const backup = JSON.parse(await decryptBackup(await file.text(), pass)); applyBackupObject(backup); parityToast('Backup رمزدار بازیابی شد.'); }
    catch { parityToast('رمز یا فایل Backup صحیح نیست.'); }
  });
};

/* ------------------------- Settings کامل‌تر ----------------------------- */

const NOTIFICATION_KEY = 'hesabyar_web_notifications_v1';

renderSettings = function renderSettingsCompat() {
  const notifications = localStorage.getItem(NOTIFICATION_KEY) !== 'false';
  root.innerHTML = `
    <section class="panel"><div class="panel-head"><div><h1>تنظیمات</h1><p>تنظیمات PWA روی همین دستگاه ذخیره می‌شوند.</p></div></div><div class="panel-body settings-list">
      <div class="setting-row"><div><strong>واحد پول</strong><span>روی نتایج و ورودی‌های مالی اثر می‌گذارد.</span></div><select class="select" id="currencySelect" style="width:auto"><option value="تومان" ${state.currency === 'تومان' ? 'selected' : ''}>تومان</option><option value="ریال" ${state.currency === 'ریال' ? 'selected' : ''}>ریال</option></select></div>
      <div class="setting-row"><div><strong>ظاهر</strong><span>روشن، تاریک یا هماهنگ با سیستم.</span></div><select class="select" id="themeSelect" style="width:auto"><option value="system" ${state.theme === 'system' ? 'selected' : ''}>سیستم</option><option value="light" ${state.theme === 'light' ? 'selected' : ''}>روشن</option><option value="dark" ${state.theme === 'dark' ? 'selected' : ''}>تاریک</option></select></div>
      <div class="setting-row"><div><strong>اعلان‌ها</strong><span>ترجیح محلی نسخه Web؛ اعلان واقعی نیازمند مجوز مرورگر است.</span></div><input id="notificationsToggle" type="checkbox" ${notifications ? 'checked' : ''}></div>
      <div class="setting-row"><div><strong>مجوز Notification مرورگر</strong><span id="notificationPermission">${'Notification' in window ? Notification.permission : 'پشتیبانی نمی‌شود'}</span></div><button id="requestNotification" class="secondary-button" ${'Notification' in window ? '' : 'disabled'}>درخواست مجوز</button></div>
      <div class="setting-row"><div><strong>Service Worker</strong><span>کش آفلاین فایل‌های Web.</span></div><strong id="settingsWorkerStatus">در حال بررسی…</strong></div>
      <div class="setting-row"><div><strong>خروجی و Backup</strong><span>CSV، XLSX، PDF/Print و Restore.</span></div><button id="openDataTools" class="secondary-button">مدیریت داده</button></div>
      <div class="setting-row"><div><strong>نسخه</strong><span>نسخه وب هم‌راستا با حسابیار 3.0.0.</span></div><strong>3.0.0 · Web build 2</strong></div>
    </div></section>`;

  document.getElementById('currencySelect').addEventListener('change', event => { state.currency = event.target.value; localStorage.setItem(STORAGE_KEYS.currency, state.currency); });
  document.getElementById('themeSelect').addEventListener('change', event => { state.theme = event.target.value; localStorage.setItem(STORAGE_KEYS.theme, state.theme); applyTheme(); });
  document.getElementById('notificationsToggle').addEventListener('change', event => localStorage.setItem(NOTIFICATION_KEY, String(event.target.checked)));
  document.getElementById('requestNotification').addEventListener('click', async () => {
    if (!('Notification' in window)) return;
    const permission = await Notification.requestPermission();
    document.getElementById('notificationPermission').textContent = permission;
  });
  document.getElementById('openDataTools').addEventListener('click', () => navigate('dataTools'));
  updateWorkerStatus();
};

/* ----------------------- PWA Shortcuts و Back ---------------------------- */

/* Query پارامتر PWA Shortcut مانند ?tool=discount را باز می‌کند. */
function openStartupShortcut() {
  const params = new URLSearchParams(location.search);
  const tool = params.get('tool');
  if (tool && TOOLS.some(item => item.id === tool)) openTool(tool);
}

/* Navigation را وارد History مرورگر می‌کند تا Back مثل Android به صفحه قبل برگردد. */
const compatNavigateBase = navigate;
navigate = function navigateWithBrowserHistory(page, options = {}) {
  if (!options.fromPopState) history.pushState({ page }, '', location.pathname + location.search + `#${encodeURIComponent(page)}`);
  compatNavigateBase(page);
};

const compatOpenToolBase = openTool;
openTool = function openToolWithHistory(id, options = {}) {
  if (!options.fromPopState) history.pushState({ page:'tool', tool:id }, '', location.pathname + location.search + `#tool-${encodeURIComponent(id)}`);
  compatOpenToolBase(id);
};

window.addEventListener('popstate', event => {
  closeParityDrawer();
  const entry = event.state;
  if (entry?.page === 'tool' && entry.tool) compatOpenToolBase(entry.tool);
  else compatNavigateBase(entry?.page || 'home');
});

/* وضعیت اولیه History بدون ایجاد رکورد اضافی ثبت می‌شود. */
history.replaceState({ page:'home' }, '', location.pathname + location.search + '#home');
openStartupShortcut();
