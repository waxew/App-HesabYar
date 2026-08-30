/*
 * موتور و رابط مستقل نسخه وب حسابیار.
 * فرمول‌ها با CalculationEngine.kt نسخه Android هم‌راستا هستند تا خروجی دو نسخه متفاوت نشود.
 */

/* کلیدهای LocalStorage برای حفظ تنظیمات و تاریخچه بین اجراها. */
const STORAGE_KEYS = {
  history: 'hesabyar_web_history_v1',
  currency: 'hesabyar_web_currency_v1',
  theme: 'hesabyar_web_theme_v1'
};

/* مدل ابزارها برای ساخت صفحه خانه. */
const TOOLS = [
  { id: 'discount', title: 'تخفیف', subtitle: 'قیمت نهایی، صرفه‌جویی و تخفیف واقعی', icon: '٪' },
  { id: 'profit', title: 'سود', subtitle: 'سود خالص، Markup و حاشیه سود', icon: '↗' },
  { id: 'target', title: 'قیمت فروش', subtitle: 'قیمت مناسب برای سود هدف', icon: '¤' },
  { id: 'percentage', title: 'درصد', subtitle: 'سه محاسبه رایج درصد', icon: '%' },
  { id: 'change', title: 'کم / زیاد', subtitle: 'درصد افزایش یا کاهش', icon: '↕' },
  { id: 'tax', title: 'مالیات', subtitle: 'مبلغ مالیات و مبلغ نهایی', icon: '+' },
  { id: 'compare', title: 'مقایسه خرید', subtitle: 'مقایسه قیمت واحد دو کالا', icon: '⇄' },
  { id: 'breakEven', title: 'سربه‌سر', subtitle: 'حداقل فروش برای پوشش هزینه ثابت', icon: '=' }
];

/* State سبک برنامه؛ داده حساس یا سروری در این نسخه وجود ندارد. */
const state = {
  page: 'home',
  tool: null,
  currency: localStorage.getItem(STORAGE_KEYS.currency) || 'تومان',
  theme: localStorage.getItem(STORAGE_KEYS.theme) || 'system',
  history: loadHistory()
};

/* رفرنس المان‌های ثابت صفحه. */
const root = document.getElementById('app');
const navButtons = Array.from(document.querySelectorAll('[data-nav]'));
const themeButton = document.getElementById('themeButton');
const connectionBadge = document.getElementById('connectionBadge');
const workerStatus = document.getElementById('workerStatus');

/* تاریخچه ذخیره‌شده را با fallback امن می‌خواند. */
function loadHistory() {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEYS.history) || '[]');
    return Array.isArray(parsed) ? parsed.slice(0, 100) : [];
  } catch {
    return [];
  }
}

/* تاریخچه را محدود به 100 رکورد نگه می‌دارد. */
function persistHistory() {
  state.history = state.history.slice(0, 100);
  localStorage.setItem(STORAGE_KEYS.history, JSON.stringify(state.history));
}

/* ورودی عددی فارسی/انگلیسی، جداکننده هزارگان و اعشار را پاک‌سازی می‌کند. */
function toNumber(value) {
  if (value == null) return null;
  const map = { '۰':'0','۱':'1','۲':'2','۳':'3','۴':'4','۵':'5','۶':'6','۷':'7','۸':'8','۹':'9','٠':'0','١':'1','٢':'2','٣':'3','٤':'4','٥':'5','٦':'6','٧':'7','٨':'8','٩':'9' };
  const normalized = String(value)
    .replace(/[۰-۹٠-٩]/g, char => map[char])
    .replace(/,/g, '')
    .replace(/٬/g, '')
    .replace(/٫/g, '.')
    .trim();
  if (!normalized) return null;
  const number = Number(normalized);
  return Number.isFinite(number) ? number : null;
}

/* فرمت عدد مالی با جداکننده هزارگان. */
function money(value) {
  if (!Number.isFinite(value)) return '—';
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(value);
}

/* فرمت عدد عمومی بدون صفرهای اضافی. */
function clean(value) {
  if (!Number.isFinite(value)) return '—';
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 4 }).format(value);
}

/* فرمت درصد. */
function percent(value) {
  return `${clean(value)}٪`;
}

/* Escaping برای مقادیر متنی که از State وارد HTML می‌شوند. */
function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>'"]/g, char => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', "'":'&#039;', '"':'&quot;' }[char]));
}

/* -------------------------- Calculation Engine -------------------------- */

/* یک یا دو تخفیف متوالی؛ عین منطق نسخه Android. */
function calculateDiscount(price, firstDiscount, secondDiscount = 0) {
  if (price < 0 || firstDiscount < 0 || firstDiscount > 100 || secondDiscount < 0 || secondDiscount > 100) return null;
  const finalPrice = price * (1 - firstDiscount / 100) * (1 - secondDiscount / 100);
  const savedAmount = price - finalPrice;
  const effectiveDiscount = price > 0 ? (1 - finalPrice / price) * 100 : 0;
  return { finalPrice, savedAmount, effectiveDiscount };
}

/* سود، هزینه واقعی، Markup و Margin. */
function calculateProfit(purchaseCost, extraCost, salePrice) {
  if (purchaseCost < 0 || extraCost < 0 || salePrice < 0) return null;
  const totalCost = purchaseCost + extraCost;
  const profit = salePrice - totalCost;
  const markupPercent = totalCost > 0 ? profit / totalCost * 100 : null;
  const marginPercent = salePrice > 0 ? profit / salePrice * 100 : null;
  return { totalCost, profit, markupPercent, marginPercent };
}

/* قیمت فروش برای Markup یا Margin هدف. */
function calculateTargetPrice(purchaseCost, extraCost, targetPercent, useMargin) {
  if (purchaseCost < 0 || extraCost < 0 || targetPercent < 0) return null;
  const totalCost = purchaseCost + extraCost;
  if (useMargin && targetPercent >= 100) return null;
  return useMargin ? totalCost / (1 - targetPercent / 100) : totalCost * (1 + targetPercent / 100);
}

/* X درصد از Y. */
function percentageOf(percentValue, value) {
  if (percentValue < 0 || value < 0) return null;
  return percentValue / 100 * value;
}

/* X چند درصد Y است. */
function whatPercent(part, whole) {
  if (part < 0 || whole <= 0) return null;
  return part / whole * 100;
}

/* درصد تغییر. */
function percentageChange(oldValue, newValue) {
  if (oldValue <= 0 || newValue < 0) return null;
  return (newValue - oldValue) / oldValue * 100;
}

/* مالیات. */
function calculateTax(amount, ratePercent) {
  if (amount < 0 || ratePercent < 0) return null;
  const taxAmount = amount * ratePercent / 100;
  return { taxAmount, totalAmount: amount + taxAmount };
}

/* مقایسه قیمت واحد دو کالا. */
function compareProducts(firstPrice, firstQuantity, secondPrice, secondQuantity) {
  if (firstPrice < 0 || secondPrice < 0 || firstQuantity <= 0 || secondQuantity <= 0) return null;
  const firstUnitPrice = firstPrice / firstQuantity;
  const secondUnitPrice = secondPrice / secondQuantity;
  const winner = firstUnitPrice <= secondUnitPrice ? 1 : 2;
  const cheaper = Math.min(firstUnitPrice, secondUnitPrice);
  const expensive = Math.max(firstUnitPrice, secondUnitPrice);
  const savingPercent = expensive > 0 ? (1 - cheaper / expensive) * 100 : 0;
  return { firstUnitPrice, secondUnitPrice, winner, savingPercent };
}

/* تعداد فروش سربه‌سر. */
function breakEvenUnits(fixedCost, profitPerUnit) {
  if (fixedCost < 0 || profitPerUnit <= 0) return null;
  return Math.ceil(fixedCost / profitPerUnit);
}

/* ------------------------------ UI Helpers ------------------------------ */

/* فیلد عددی استاندارد. */
function numberField(id, label, placeholder = '0', hint = '') {
  return `
    <div class="field">
      <label for="${id}">${label}</label>
      <input class="input" id="${id}" inputmode="decimal" autocomplete="off" placeholder="${placeholder}">
      ${hint ? `<small>${hint}</small>` : ''}
    </div>`;
}

/* کارت خروجی واحد برای تمام ابزارها. */
function renderResult(container, { label, value, rows = [], negative = false, save }) {
  container.innerHTML = `
    <div class="result-card ${negative ? 'is-negative' : ''}">
      <span class="result-label">${escapeHtml(label)}</span>
      <strong class="result-value">${escapeHtml(value)}</strong>
      <div class="result-list">
        ${rows.map(row => `<div class="result-row"><span>${escapeHtml(row[0])}</span><strong>${escapeHtml(row[1])}</strong></div>`).join('')}
      </div>
      ${save ? `<div class="result-actions"><button class="primary-button" id="saveResultButton">ذخیره در تاریخچه</button></div>` : ''}
    </div>`;
  if (save) document.getElementById('saveResultButton')?.addEventListener('click', save);
}

/* ذخیره نتیجه با زمان محلی. */
function saveHistory(title, details, result) {
  state.history.unshift({
    id: Date.now(),
    createdAt: new Date().toISOString(),
    title,
    details,
    result
  });
  persistHistory();
}

/* قالب مشترک صفحه ابزار. */
function toolShell(title, subtitle, body) {
  root.innerHTML = `
    <section class="panel">
      <div class="panel-head">
        <div><h1>${title}</h1><p>${subtitle}</p></div>
        <button class="secondary-button" id="backHomeButton" type="button">بازگشت</button>
      </div>
      <div class="panel-body">${body}</div>
    </section>`;
  document.getElementById('backHomeButton')?.addEventListener('click', () => navigate('home'));
}

/* ------------------------------- Pages ---------------------------------- */

/* صفحه خانه و ابزارها. */
function renderHome() {
  root.innerHTML = `
    <section class="hero">
      <article class="hero-main">
        <span class="eyebrow">HesabYar Web · v3.0</span>
        <h1>حساب‌های روزمره، سریع و آفلاین.</h1>
        <p>نسخه وب مستقل حسابیار با همان منطق محاسباتی نسخه Android. بعد از اولین بارگذاری، Service Worker فایل‌های اصلی را ذخیره می‌کند تا بخش محاسبات بدون اینترنت هم اجرا شود.</p>
      </article>
      <aside class="hero-side">
        <div class="status-row"><span>نسخه وب</span><strong class="online-dot">فعال</strong></div>
        <div class="status-row"><span>واحد پول</span><strong>${escapeHtml(state.currency)}</strong></div>
        <div class="status-row"><span>تاریخچه</span><strong>${state.history.length} مورد</strong></div>
      </aside>
    </section>

    <div class="section-heading">
      <div><h2>ابزارهای حسابیار</h2><p>برای شروع یکی از ابزارها را انتخاب کن.</p></div>
    </div>

    <section class="tool-grid">
      ${TOOLS.map(tool => `
        <button class="tool-card" type="button" data-tool="${tool.id}">
          <span class="tool-icon">${tool.icon}</span>
          <strong>${tool.title}</strong>
          <span>${tool.subtitle}</span>
        </button>`).join('')}
    </section>`;

  document.querySelectorAll('[data-tool]').forEach(button => {
    button.addEventListener('click', () => openTool(button.dataset.tool));
  });
}

/* ابزار تخفیف. */
function renderDiscount() {
  toolShell('تخفیف', 'تخفیف دوم اختیاری است و روی مبلغ باقی‌مانده اعمال می‌شود.', `
    <div class="form-grid">
      ${numberField('price', `قیمت اصلی (${state.currency})`)}
      ${numberField('firstDiscount', 'تخفیف اول', '0')}
      ${numberField('secondDiscount', 'تخفیف دوم (اختیاری)', '0')}
    </div>
    <div id="validation"></div><div id="result"></div>`);

  const recalc = () => {
    const price = toNumber(document.getElementById('price').value);
    const first = toNumber(document.getElementById('firstDiscount').value);
    const second = toNumber(document.getElementById('secondDiscount').value) ?? 0;
    const resultBox = document.getElementById('result');
    const validation = document.getElementById('validation');
    validation.innerHTML = '';
    resultBox.innerHTML = '';
    if (price == null || first == null) return;
    const result = calculateDiscount(price, first, second);
    if (!result) {
      validation.innerHTML = '<div class="validation-message">درصد تخفیف باید بین ۰ تا ۱۰۰ باشد و قیمت نمی‌تواند منفی باشد.</div>';
      return;
    }
    renderResult(resultBox, {
      label: 'قیمت نهایی',
      value: `${money(result.finalPrice)} ${state.currency}`,
      rows: [['صرفه‌جویی', `${money(result.savedAmount)} ${state.currency}`], ['تخفیف واقعی', percent(result.effectiveDiscount)]],
      save: () => saveHistory('تخفیف', `${money(price)} با ${percent(result.effectiveDiscount)} تخفیف واقعی`, `${money(result.finalPrice)} ${state.currency}`)
    });
  };
  ['price','firstDiscount','secondDiscount'].forEach(id => document.getElementById(id).addEventListener('input', recalc));
}

/* ابزار سود. */
function renderProfit() {
  toolShell('سود واقعی', 'هزینه جانبی را هم وارد کن تا سود، Markup و Margin دقیق محاسبه شوند.', `
    <div class="form-grid">
      ${numberField('purchaseCost', `قیمت خرید (${state.currency})`)}
      ${numberField('extraCost', `هزینه جانبی (${state.currency})`, '0')}
      ${numberField('salePrice', `قیمت فروش (${state.currency})`)}
    </div><div id="result"></div>`);

  const recalc = () => {
    const purchase = toNumber(document.getElementById('purchaseCost').value);
    const extra = toNumber(document.getElementById('extraCost').value) ?? 0;
    const sale = toNumber(document.getElementById('salePrice').value);
    const box = document.getElementById('result');
    box.innerHTML = '';
    if (purchase == null || sale == null) return;
    const result = calculateProfit(purchase, extra, sale);
    if (!result) return;
    const loss = result.profit < 0;
    renderResult(box, {
      label: loss ? 'زیان خالص' : 'سود خالص',
      value: `${money(Math.abs(result.profit))} ${state.currency}`,
      negative: loss,
      rows: [
        ['هزینه واقعی', `${money(result.totalCost)} ${state.currency}`],
        ['درصد سود روی هزینه', result.markupPercent == null ? 'تعریف‌نشده' : percent(result.markupPercent)],
        ['حاشیه سود', result.marginPercent == null ? 'تعریف‌نشده' : percent(result.marginPercent)]
      ],
      save: () => saveHistory('سود', `هزینه ${money(result.totalCost)} / فروش ${money(sale)}`, `${loss ? 'زیان' : 'سود'} ${money(Math.abs(result.profit))} ${state.currency}`)
    });
  };
  ['purchaseCost','extraCost','salePrice'].forEach(id => document.getElementById(id).addEventListener('input', recalc));
}

/* ابزار قیمت فروش هدف. */
function renderTarget() {
  toolShell('قیمت فروش پیشنهادی', 'سود روی هزینه یا حاشیه سود واقعی را هدف‌گذاری کن.', `
    <div class="form-grid">
      ${numberField('targetCost', `هزینه خرید (${state.currency})`)}
      ${numberField('targetExtra', `هزینه جانبی (${state.currency})`, '0')}
      ${numberField('targetPercent', 'درصد هدف', '0')}
      <div class="field">
        <label for="targetMode">روش محاسبه</label>
        <select class="select" id="targetMode"><option value="markup">سود روی هزینه (Markup)</option><option value="margin">حاشیه سود (Margin)</option></select>
      </div>
    </div><div id="validation"></div><div id="result"></div>`);

  const recalc = () => {
    const cost = toNumber(document.getElementById('targetCost').value);
    const extra = toNumber(document.getElementById('targetExtra').value) ?? 0;
    const target = toNumber(document.getElementById('targetPercent').value);
    const useMargin = document.getElementById('targetMode').value === 'margin';
    const box = document.getElementById('result');
    const validation = document.getElementById('validation');
    box.innerHTML = ''; validation.innerHTML = '';
    if (cost == null || target == null) return;
    const value = calculateTargetPrice(cost, extra, target, useMargin);
    if (value == null) {
      validation.innerHTML = '<div class="validation-message">در حالت حاشیه سود، درصد هدف باید کمتر از ۱۰۰٪ باشد.</div>';
      return;
    }
    const total = cost + extra;
    renderResult(box, {
      label: 'قیمت پیشنهادی',
      value: `${money(value)} ${state.currency}`,
      rows: [['هزینه واقعی', `${money(total)} ${state.currency}`], ['روش', useMargin ? 'حاشیه سود' : 'سود روی هزینه']],
      save: () => saveHistory('قیمت فروش', `هزینه ${money(total)} / هدف ${percent(target)}`, `${money(value)} ${state.currency}`)
    });
  };
  ['targetCost','targetExtra','targetPercent'].forEach(id => document.getElementById(id).addEventListener('input', recalc));
  document.getElementById('targetMode').addEventListener('change', recalc);
}

/* ابزار درصد سه‌حالته. */
function renderPercentage() {
  toolShell('محاسبه درصد', 'سه حالت رایج درصد در یک ابزار.', `
    <div class="form-grid">
      <div class="field full"><label for="percentMode">نوع محاسبه</label><select class="select" id="percentMode"><option value="of">X٪ از Y</option><option value="what">X چند٪ Y است؟</option><option value="change">درصد تغییر</option></select></div>
      ${numberField('percentFirst', 'مقدار اول')}
      ${numberField('percentSecond', 'مقدار دوم')}
    </div><div id="validation"></div><div id="result"></div>`);

  const recalc = () => {
    const first = toNumber(document.getElementById('percentFirst').value);
    const second = toNumber(document.getElementById('percentSecond').value);
    const mode = document.getElementById('percentMode').value;
    const box = document.getElementById('result');
    const validation = document.getElementById('validation');
    box.innerHTML = ''; validation.innerHTML = '';
    if (first == null || second == null) return;
    let value = null;
    if (mode === 'of') value = percentageOf(first, second);
    if (mode === 'what') value = whatPercent(first, second);
    if (mode === 'change') value = percentageChange(first, second);
    if (value == null) {
      validation.innerHTML = '<div class="validation-message">مقادیر واردشده برای این نوع محاسبه معتبر نیستند.</div>';
      return;
    }
    const resultText = mode === 'of' ? clean(value) : percent(value);
    renderResult(box, { label: 'نتیجه', value: resultText, save: () => saveHistory('درصد', `${clean(first)} و ${clean(second)}`, resultText) });
  };
  ['percentFirst','percentSecond'].forEach(id => document.getElementById(id).addEventListener('input', recalc));
  document.getElementById('percentMode').addEventListener('change', recalc);
}

/* ابزار افزایش یا کاهش. */
function renderChange() {
  toolShell('افزایش / کاهش', 'تغییر مقدار قبلی تا مقدار جدید را به درصد ببین.', `
    <div class="form-grid">${numberField('oldValue','مقدار قبلی')}${numberField('newValue','مقدار جدید')}</div>
    <div id="validation"></div><div id="result"></div>`);

  const recalc = () => {
    const oldValue = toNumber(document.getElementById('oldValue').value);
    const newValue = toNumber(document.getElementById('newValue').value);
    const box = document.getElementById('result');
    const validation = document.getElementById('validation');
    box.innerHTML = ''; validation.innerHTML = '';
    if (oldValue == null || newValue == null) return;
    const value = percentageChange(oldValue, newValue);
    if (value == null) {
      validation.innerHTML = '<div class="validation-message">مقدار قبلی باید بزرگ‌تر از صفر باشد.</div>';
      return;
    }
    renderResult(box, {
      label: value >= 0 ? 'افزایش' : 'کاهش',
      value: percent(Math.abs(value)),
      negative: value < 0,
      rows: [['اختلاف عددی', clean(newValue - oldValue)]],
      save: () => saveHistory('تغییر درصد', `${clean(oldValue)} → ${clean(newValue)}`, `${value >= 0 ? 'افزایش' : 'کاهش'} ${percent(Math.abs(value))}`)
    });
  };
  ['oldValue','newValue'].forEach(id => document.getElementById(id).addEventListener('input', recalc));
}

/* ابزار مالیات. */
function renderTax() {
  toolShell('مالیات', 'نرخ مالیات را روی مبلغ پایه اعمال کن.', `
    <div class="form-grid">${numberField('taxAmount',`مبلغ پایه (${state.currency})`)}${numberField('taxRate','نرخ مالیات','0')}</div>
    <div id="result"></div>`);

  const recalc = () => {
    const amount = toNumber(document.getElementById('taxAmount').value);
    const rate = toNumber(document.getElementById('taxRate').value);
    const box = document.getElementById('result'); box.innerHTML = '';
    if (amount == null || rate == null) return;
    const value = calculateTax(amount, rate);
    if (!value) return;
    renderResult(box, {
      label: 'مبلغ نهایی', value: `${money(value.totalAmount)} ${state.currency}`,
      rows: [['مالیات', `${money(value.taxAmount)} ${state.currency}`]],
      save: () => saveHistory('مالیات', `${money(amount)} + ${percent(rate)}`, `${money(value.totalAmount)} ${state.currency}`)
    });
  };
  ['taxAmount','taxRate'].forEach(id => document.getElementById(id).addEventListener('input', recalc));
}

/* ابزار مقایسه خرید. */
function renderCompare() {
  toolShell('مقایسه دو کالا', 'واحد مقدار برای هر دو کالا باید یکسان باشد؛ مثلاً هر دو گرم یا هر دو عدد.', `
    <div class="form-grid">
      ${numberField('p1',`قیمت کالای اول (${state.currency})`)}${numberField('q1','مقدار کالای اول')}
      ${numberField('p2',`قیمت کالای دوم (${state.currency})`)}${numberField('q2','مقدار کالای دوم')}
    </div><div id="validation"></div><div id="result"></div>`);

  const recalc = () => {
    const values = ['p1','q1','p2','q2'].map(id => toNumber(document.getElementById(id).value));
    const box = document.getElementById('result');
    const validation = document.getElementById('validation');
    box.innerHTML = ''; validation.innerHTML = '';
    if (values.some(value => value == null)) return;
    const result = compareProducts(...values);
    if (!result) {
      validation.innerHTML = '<div class="validation-message">مقدار/تعداد هر کالا باید بزرگ‌تر از صفر باشد.</div>';
      return;
    }
    const winner = result.winner === 1 ? 'کالای اول' : 'کالای دوم';
    const winnerUnit = result.winner === 1 ? result.firstUnitPrice : result.secondUnitPrice;
    renderResult(box, {
      label: `${winner} به‌صرفه‌تر است`, value: `${money(winnerUnit)} ${state.currency} / واحد`,
      rows: [['کالای اول / واحد', `${money(result.firstUnitPrice)} ${state.currency}`], ['کالای دوم / واحد', `${money(result.secondUnitPrice)} ${state.currency}`], ['مزیت تقریبی', percent(result.savingPercent)]],
      save: () => saveHistory('مقایسه خرید', 'مقایسه قیمت واحد دو کالا', `${winner} حدود ${percent(result.savingPercent)} به‌صرفه‌تر`)
    });
  };
  ['p1','q1','p2','q2'].forEach(id => document.getElementById(id).addEventListener('input', recalc));
}

/* ابزار نقطه سربه‌سر. */
function renderBreakEven() {
  toolShell('نقطه سربه‌سر', 'هزینه ثابت و سود خالص هر فروش را وارد کن.', `
    <div class="form-grid">${numberField('fixedCost',`هزینه ثابت (${state.currency})`)}${numberField('unitProfit',`سود خالص هر محصول (${state.currency})`)}</div>
    <div id="validation"></div><div id="result"></div>`);

  const recalc = () => {
    const fixed = toNumber(document.getElementById('fixedCost').value);
    const unit = toNumber(document.getElementById('unitProfit').value);
    const box = document.getElementById('result');
    const validation = document.getElementById('validation');
    box.innerHTML = ''; validation.innerHTML = '';
    if (fixed == null || unit == null) return;
    const count = breakEvenUnits(fixed, unit);
    if (count == null) {
      validation.innerHTML = '<div class="validation-message">سود هر محصول باید بزرگ‌تر از صفر باشد.</div>';
      return;
    }
    renderResult(box, {
      label: 'حداقل فروش برای سربه‌سر', value: `${count} محصول`,
      rows: [['بعد از این نقطه', 'فروش وارد محدوده سود می‌شود']],
      save: () => saveHistory('نقطه سربه‌سر', `هزینه ${money(fixed)} / سود واحد ${money(unit)}`, `${count} محصول`)
    });
  };
  ['fixedCost','unitProfit'].forEach(id => document.getElementById(id).addEventListener('input', recalc));
}

/* صفحه تاریخچه. */
function renderHistory() {
  root.innerHTML = `
    <section class="panel">
      <div class="panel-head"><div><h1>تاریخچه</h1><p>محاسبات فقط در همین مرورگر ذخیره می‌شوند.</p></div>${state.history.length ? '<button class="danger-button" id="clearHistory">پاک کردن همه</button>' : ''}</div>
      <div class="panel-body">
        ${state.history.length ? `<div class="history-list">${state.history.map(item => `
          <article class="history-item">
            <div class="history-top"><h3>${escapeHtml(item.title)}</h3><time>${new Intl.DateTimeFormat('fa-IR', { dateStyle:'short', timeStyle:'short' }).format(new Date(item.createdAt))}</time></div>
            <p>${escapeHtml(item.details)}</p><strong>${escapeHtml(item.result)}</strong>
          </article>`).join('')}</div>` : '<div class="empty-state">هنوز محاسبه‌ای در تاریخچه ذخیره نشده است.</div>'}
      </div>
    </section>`;

  document.getElementById('clearHistory')?.addEventListener('click', () => {
    state.history = [];
    persistHistory();
    renderHistory();
  });
}

/* صفحه تنظیمات. */
function renderSettings() {
  root.innerHTML = `
    <section class="panel">
      <div class="panel-head"><div><h1>تنظیمات</h1><p>تنظیمات نسخه وب روی همین دستگاه ذخیره می‌شوند.</p></div></div>
      <div class="panel-body settings-list">
        <div class="setting-row"><div><strong>واحد پول</strong><span>روی برچسب نتایج و ورودی‌های مالی اثر می‌گذارد.</span></div><select class="select" id="currencySelect" style="width:auto"><option value="تومان" ${state.currency === 'تومان' ? 'selected' : ''}>تومان</option><option value="ریال" ${state.currency === 'ریال' ? 'selected' : ''}>ریال</option></select></div>
        <div class="setting-row"><div><strong>ظاهر</strong><span>روشن، تاریک یا هماهنگ با سیستم.</span></div><select class="select" id="themeSelect" style="width:auto"><option value="system" ${state.theme === 'system' ? 'selected' : ''}>سیستم</option><option value="light" ${state.theme === 'light' ? 'selected' : ''}>روشن</option><option value="dark" ${state.theme === 'dark' ? 'selected' : ''}>تاریک</option></select></div>
        <div class="setting-row"><div><strong>Service Worker</strong><span>برای کش فایل‌ها و اجرای آفلاین نسخه وب.</span></div><strong id="settingsWorkerStatus">در حال بررسی…</strong></div>
        <div class="setting-row"><div><strong>نسخه</strong><span>نسخه وب هم‌راستا با پروژه حسابیار.</span></div><strong>3.0.0 Web PWA</strong></div>
      </div>
    </section>`;

  document.getElementById('currencySelect').addEventListener('change', event => {
    state.currency = event.target.value;
    localStorage.setItem(STORAGE_KEYS.currency, state.currency);
  });
  document.getElementById('themeSelect').addEventListener('change', event => {
    state.theme = event.target.value;
    localStorage.setItem(STORAGE_KEYS.theme, state.theme);
    applyTheme();
  });
  updateWorkerStatus();
}

/* ابزار انتخاب‌شده را نمایش می‌دهد. */
function openTool(id) {
  state.page = 'tool';
  state.tool = id;
  updateNav();
  const renderers = {
    discount: renderDiscount,
    profit: renderProfit,
    target: renderTarget,
    percentage: renderPercentage,
    change: renderChange,
    tax: renderTax,
    compare: renderCompare,
    breakEven: renderBreakEven
  };
  renderers[id]?.();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

/* Navigation اصلی. */
function navigate(page) {
  state.page = page;
  state.tool = null;
  updateNav();
  if (page === 'home') renderHome();
  if (page === 'history') renderHistory();
  if (page === 'settings') renderSettings();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

/* Bottom Navigation را با صفحه فعال هماهنگ می‌کند. */
function updateNav() {
  navButtons.forEach(button => button.classList.toggle('is-active', state.page === button.dataset.nav));
}

/* تم System/Light/Dark را اعمال می‌کند. */
function applyTheme() {
  const prefersDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches;
  const resolved = state.theme === 'system' ? (prefersDark ? 'dark' : 'light') : state.theme;
  document.documentElement.dataset.theme = resolved;
}

/* وضعیت اینترنت فقط اطلاع‌رسانی است؛ محاسبات به اینترنت وابسته نیستند. */
function updateConnectionStatus() {
  const online = navigator.onLine;
  connectionBadge.textContent = online ? 'آنلاین' : 'آفلاین · PWA';
  connectionBadge.classList.toggle('is-offline', !online);
}

/* Service Worker را در Scope همان پروژه ثبت می‌کند. */
async function registerServiceWorker() {
  if (!('serviceWorker' in navigator)) {
    workerStatus.textContent = 'پشتیبانی نمی‌شود';
    return;
  }
  try {
    const registration = await navigator.serviceWorker.register('./sw.js', { scope: './' });
    await navigator.serviceWorker.ready;
    workerStatus.textContent = 'فعال';
    workerStatus.classList.add('online-dot');
    console.info('HesabYar Service Worker scope:', registration.scope);
    updateWorkerStatus();
  } catch (error) {
    workerStatus.textContent = 'خطا';
    console.error('Service Worker registration failed:', error);
    updateWorkerStatus();
  }
}

/* وضعیت Worker در صفحه تنظیمات را نیز تازه می‌کند. */
async function updateWorkerStatus() {
  const target = document.getElementById('settingsWorkerStatus');
  if (!target) return;
  if (!('serviceWorker' in navigator)) {
    target.textContent = 'پشتیبانی نمی‌شود';
    return;
  }
  const registration = await navigator.serviceWorker.getRegistration('./');
  target.textContent = registration?.active ? 'فعال' : registration ? 'در حال فعال‌سازی' : 'ثبت نشده';
}

/* Eventهای ثابت. */
navButtons.forEach(button => button.addEventListener('click', () => navigate(button.dataset.nav)));
themeButton.addEventListener('click', () => {
  state.theme = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
  localStorage.setItem(STORAGE_KEYS.theme, state.theme);
  applyTheme();
});
window.addEventListener('online', updateConnectionStatus);
window.addEventListener('offline', updateConnectionStatus);
window.matchMedia?.('(prefers-color-scheme: dark)').addEventListener?.('change', () => { if (state.theme === 'system') applyTheme(); });

/* راه‌اندازی برنامه. */
applyTheme();
updateConnectionStatus();
updateNav();
renderHome();
registerServiceWorker();
