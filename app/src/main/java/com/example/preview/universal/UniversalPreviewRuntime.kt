package com.example.preview.universal

/**
 * UniversalPreviewRuntime
 * 
 * Generates the high-fidelity interactive browser preview bundle (HTML, CSS, JS).
 * Includes Material 3 renderer, reactive state engine, navigation engine, and mock hardware APIs.
 * Supports full interactive Calculator, Grid layouts, and multi-line Compose/Flutter components.
 */
object UniversalPreviewRuntime {

    fun generateHtml(document: PreviewDocument): String {
        val initialScreen = document.screens.find { it.isInitial } ?: document.screens.firstOrNull()
        val allScreensJson = serializeScreensToJson(document.screens)
        val initialScreenId = initialScreen?.id ?: "main"
        val theme = document.theme

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <title>${escapeHtml(document.appTitle)} - Universal Live Preview</title>
            <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&family=Plus+Jakarta+Sans:wght@400;600;700&display=swap" rel="stylesheet">
            <style>
                :root {
                    --md-sys-color-primary: ${theme.primaryColor};
                    --md-sys-color-on-primary: ${theme.onPrimaryColor};
                    --md-sys-color-primary-container: ${theme.primaryContainer};
                    --md-sys-color-secondary: ${theme.secondaryColor};
                    --md-sys-color-background: ${theme.backgroundColor};
                    --md-sys-color-surface: ${theme.surfaceColor};
                    --md-sys-color-on-surface: ${theme.onSurfaceColor};
                    --md-sys-color-inverse-primary: ${theme.inversePrimary};
                    --md-sys-color-outline: #79747E;
                }

                * {
                    box-sizing: border-box;
                    margin: 0;
                    padding: 0;
                    -webkit-tap-highlight-color: transparent;
                }

                body {
                    font-family: 'Roboto', 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    background-color: #090d16;
                    color: var(--md-sys-color-on-surface);
                    min-height: 100vh;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: flex-start;
                    padding: 8px 4px;
                    overflow-x: hidden;
                }

                /* Framework Meta Banner */
                .preview-meta-bar {
                    width: 100%;
                    max-width: 380px;
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    margin-bottom: 8px;
                    padding: 6px 12px;
                    background: #161b22;
                    border: 1px solid #30363d;
                    border-radius: 12px;
                    font-size: 11.5px;
                    color: #c9d1d9;
                }

                .framework-badge {
                    display: inline-flex;
                    align-items: center;
                    gap: 5px;
                    background: ${document.framework.badgeColor}22;
                    color: ${document.framework.badgeColor};
                    border: 1px solid ${document.framework.badgeColor}66;
                    padding: 2px 8px;
                    border-radius: 20px;
                    font-weight: 600;
                }

                /* Realistic Phone Device Frame */
                .phone-frame {
                    width: 100%;
                    max-width: 380px;
                    height: 720px;
                    background: var(--md-sys-color-surface);
                    border: 3.5px solid #30363d;
                    border-radius: 36px;
                    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.6), 0 0 0 1px rgba(255, 255, 255, 0.05);
                    display: flex;
                    flex-direction: column;
                    position: relative;
                    overflow: hidden;
                }

                /* Mobile Status Bar with Vector SVGs */
                .status-bar {
                    height: 32px;
                    padding: 0 16px;
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    font-size: 12px;
                    font-weight: 600;
                    color: var(--md-sys-color-on-surface);
                    background: transparent;
                    z-index: 10;
                }

                .notch {
                    width: 80px;
                    height: 12px;
                    background: #000;
                    border-radius: 0 0 10px 10px;
                    margin: 0 auto;
                }

                .status-icons {
                    display: flex;
                    gap: 6px;
                    align-items: center;
                }

                .svg-icon {
                    width: 14px;
                    height: 14px;
                    fill: currentColor;
                }

                /* App Content Area */
                .app-viewport {
                    flex: 1;
                    display: flex;
                    flex-direction: column;
                    overflow-y: auto;
                    position: relative;
                    background: var(--md-sys-color-background);
                }

                /* Material 3 App Bar */
                .m3-app-bar {
                    background: var(--md-sys-color-surface);
                    color: var(--md-sys-color-on-surface);
                    padding: 12px 16px;
                    display: flex;
                    align-items: center;
                    gap: 12px;
                    box-shadow: 0 1px 3px rgba(0,0,0,0.08);
                    font-size: 18px;
                    font-weight: 600;
                    position: sticky;
                    top: 0;
                    z-index: 5;
                }

                /* M3 Components */
                .m3-column {
                    display: flex;
                    flex-direction: column;
                    gap: 12px;
                    padding: 16px;
                    flex: 1;
                }

                .m3-row {
                    display: flex;
                    flex-direction: row;
                    align-items: center;
                    gap: 10px;
                }

                .m3-grid {
                    display: grid;
                    grid-template-columns: repeat(4, 1fr);
                    gap: 10px;
                    width: 100%;
                    padding: 10px 0;
                }

                .m3-card {
                    background: var(--md-sys-color-surface);
                    border: 1px solid rgba(0,0,0,0.08);
                    border-radius: 16px;
                    padding: 16px;
                    box-shadow: 0 2px 8px rgba(0,0,0,0.04);
                    transition: transform 0.15s ease, box-shadow 0.15s ease;
                }

                .m3-text {
                    color: var(--md-sys-color-on-surface);
                    line-height: 1.4;
                }

                .m3-button {
                    background: var(--md-sys-color-primary);
                    color: var(--md-sys-color-on-primary);
                    border: none;
                    border-radius: 20px;
                    padding: 10px 20px;
                    font-size: 14px;
                    font-weight: 600;
                    cursor: pointer;
                    display: inline-flex;
                    align-items: center;
                    justify-content: center;
                    gap: 8px;
                    transition: background 0.2s, transform 0.1s;
                }
                .m3-button:active {
                    transform: scale(0.96);
                    opacity: 0.9;
                }

                .m3-outlined-button {
                    background: transparent;
                    color: var(--md-sys-color-primary);
                    border: 1.5px solid var(--md-sys-color-primary);
                    border-radius: 20px;
                    padding: 9px 18px;
                    font-size: 14px;
                    font-weight: 600;
                    cursor: pointer;
                }

                /* Dedicated Calculator Styles */
                .m3-calc-container {
                    display: flex;
                    flex-direction: column;
                    justify-content: space-between;
                    height: 100%;
                    padding: 16px;
                }

                .m3-calc-display {
                    width: 100%;
                    min-height: 110px;
                    display: flex;
                    flex-direction: column;
                    justify-content: flex-end;
                    align-items: flex-end;
                    padding: 16px 8px;
                    text-align: right;
                    word-break: break-all;
                }

                .m3-calc-history {
                    font-size: 16px;
                    color: #79747E;
                    min-height: 22px;
                    margin-bottom: 4px;
                }

                .m3-calc-val {
                    font-size: 44px;
                    font-weight: 700;
                    color: var(--md-sys-color-on-surface);
                    letter-spacing: -1px;
                }

                .m3-calc-btn {
                    aspect-ratio: 1;
                    border-radius: 50%;
                    border: none;
                    font-size: 22px;
                    font-weight: 600;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    cursor: pointer;
                    background: #E8DEF8;
                    color: #1D192B;
                    box-shadow: 0 1px 4px rgba(0,0,0,0.1);
                    transition: transform 0.1s, opacity 0.1s;
                }
                .m3-calc-btn:active {
                    transform: scale(0.92);
                    opacity: 0.85;
                }
                .m3-calc-btn.op {
                    background: var(--md-sys-color-primary);
                    color: var(--md-sys-color-on-primary);
                }
                .m3-calc-btn.fn {
                    background: #D0BCFF;
                    color: #381E72;
                }
                .m3-calc-btn.num {
                    background: #F3EDF7;
                    color: #1D1B20;
                }
                .m3-calc-btn.wide {
                    grid-column: span 2;
                    aspect-ratio: 2.1 / 1;
                    border-radius: 36px;
                }

                .m3-text-field {
                    width: 100%;
                    padding: 12px 14px;
                    border: 1.5px solid var(--md-sys-color-outline);
                    border-radius: 8px;
                    font-size: 14px;
                    outline: none;
                    background: transparent;
                    color: var(--md-sys-color-on-surface);
                }

                .m3-fab {
                    position: absolute;
                    bottom: 24px;
                    right: 20px;
                    width: 56px;
                    height: 56px;
                    border-radius: 16px;
                    background: var(--md-sys-color-primary-container);
                    color: var(--md-sys-color-primary);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.25);
                    cursor: pointer;
                    border: none;
                }

                .m3-switch {
                    position: relative;
                    display: inline-block;
                    width: 48px;
                    height: 28px;
                }
                .m3-switch input { opacity: 0; width: 0; height: 0; }
                .slider {
                    position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0;
                    background-color: #ccc; transition: .3s; border-radius: 28px;
                }
                .slider:before {
                    position: absolute; content: ""; height: 20px; width: 20px; left: 4px; bottom: 4px;
                    background-color: white; transition: .3s; border-radius: 50%;
                }
                input:checked + .slider { background-color: var(--md-sys-color-primary); }
                input:checked + .slider:before { transform: translateX(20px); }

                /* Loading Spinner */
                .m3-progress-spinner {
                    width: 32px;
                    height: 32px;
                    border: 3.5px solid var(--md-sys-color-primary-container);
                    border-top: 3.5px solid var(--md-sys-color-primary);
                    border-radius: 50%;
                    animation: spin 0.8s linear infinite;
                    margin: 12px auto;
                }
                @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }

                .toast-overlay {
                    position: absolute;
                    bottom: 40px;
                    left: 50%;
                    transform: translateX(-50%);
                    background: #322F35;
                    color: #F5EFF7;
                    padding: 10px 20px;
                    border-radius: 24px;
                    font-size: 13px;
                    box-shadow: 0 4px 16px rgba(0,0,0,0.3);
                    z-index: 100;
                    opacity: 0;
                    pointer-events: none;
                    transition: opacity 0.3s ease, transform 0.3s ease;
                }
                .toast-overlay.active {
                    opacity: 1;
                    transform: translateX(-50%) translateY(-10px);
                }

                .nav-bar-bottom {
                    height: 20px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    background: transparent;
                }
                .home-indicator {
                    width: 120px;
                    height: 4px;
                    background: #94a3b8;
                    border-radius: 2px;
                }
            </style>
        </head>
        <body>
            <div class="preview-meta-bar">
                <span class="framework-badge">${document.framework.iconEmoji} ${document.framework.displayName}</span>
                <span style="color: #38bdf8;">● Live Universal IR</span>
            </div>

            <div class="phone-frame">
                <!-- Status Bar -->
                <div class="status-bar">
                    <span id="clock-display">09:41</span>
                    <div class="notch"></div>
                    <div class="status-icons">
                        <svg class="svg-icon" viewBox="0 0 24 24"><path d="M2 22h20V2L2 22z" /></svg>
                        <svg class="svg-icon" viewBox="0 0 24 24"><path d="M12 4C7.31 4 3.07 5.9 0 8.98L12 21 24 8.98A16.88 16.88 0 0 0 12 4zm0 4c3.48 0 6.64 1.34 9 3.53L12 19.3 3 11.53A12.92 12.92 0 0 1 12 8z"/></svg>
                        <svg class="svg-icon" viewBox="0 0 24 24"><path d="M17 4h-3V2h-4v2H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z"/></svg>
                    </div>
                </div>

                <!-- Main Viewport -->
                <div class="app-viewport" id="app-root"></div>

                <!-- Toast Overlay -->
                <div class="toast-overlay" id="toast-view">Notification</div>

                <!-- Navigation Indicator -->
                <div class="nav-bar-bottom">
                    <div class="home-indicator"></div>
                </div>
            </div>

            <script>
                try {
                    function updateClock() {
                        const now = new Date();
                        const hours = now.getHours().toString().padStart(2, '0');
                        const mins = now.getMinutes().toString().padStart(2, '0');
                        const clockEl = document.getElementById('clock-display');
                        if (clockEl) clockEl.innerText = hours + ':' + mins;
                    }
                    setInterval(updateClock, 1000);
                    updateClock();

                    const screensData = $allScreensJson;
                    let currentScreenId = "$initialScreenId";
                    let activeState = {
                        calcDisplay: '0',
                        calcHistory: '',
                        count: 0
                    };

                    function initScreen(screenId) {
                        currentScreenId = screenId;
                        const screen = screensData.find(s => s.id === screenId) || screensData[0];
                        if (screen && screen.stateVariables) {
                            activeState = Object.assign({}, activeState, screen.stateVariables);
                        }
                        renderCurrentScreen();
                    }

                    function setState(key, val) {
                        activeState[key] = val;
                        renderCurrentScreen();
                    }

                    function incrementState(key) {
                        const current = parseInt(activeState[key] || 0, 10);
                        activeState[key] = current + 1;
                        renderCurrentScreen();
                    }

                    function toggleState(key) {
                        activeState[key] = !activeState[key];
                        renderCurrentScreen();
                    }

                    function navigateTo(targetId) {
                        initScreen(targetId.toLowerCase());
                    }

                    function showToast(msg) {
                        const t = document.getElementById('toast-view');
                        if (!t) return;
                        t.innerText = msg;
                        t.classList.add('active');
                        setTimeout(() => t.classList.remove('active'), 2500);
                    }

                    // Interactive Real Calculator Engine
                    function onCalculatorInput(key) {
                        let disp = (activeState['calcDisplay'] !== undefined ? activeState['calcDisplay'].toString() : '0');
                        let hist = activeState['calcHistory'] || '';

                        if (key === 'C' || key === 'AC' || key === 'Clear' || key === 'CLEAR') {
                            disp = '0';
                            hist = '';
                        } else if (key === '⌫' || key === 'DEL' || key === 'BACK' || key === 'Backspace') {
                            disp = disp.length > 1 ? disp.slice(0, -1) : '0';
                        } else if (key === '±') {
                            disp = disp.startsWith('-') ? disp.slice(1) : '-' + disp;
                        } else if (key === '%') {
                            const val = parseFloat(disp) / 100;
                            disp = isNaN(val) ? '0' : val.toString();
                        } else if (key === '=') {
                            try {
                                const expr = (hist + disp).replace(/×/g, '*').replace(/÷/g, '/').replace(/−/g, '-');
                                const res = Function('"use strict";return (' + expr + ')')();
                                hist = hist + disp + ' =';
                                disp = (Math.round(res * 100000000) / 100000000).toString();
                            } catch (e) {
                                disp = 'Error';
                            }
                        } else if (['+', '-', '×', '÷', '*', '/'].includes(key)) {
                            hist = disp + ' ' + key + ' ';
                            disp = '0';
                        } else {
                            if (disp === '0' && key !== '.') {
                                disp = key;
                            } else if (key === '.' && disp.includes('.')) {
                                // ignore double dot
                            } else {
                                disp += key;
                            }
                        }

                        activeState['calcDisplay'] = disp;
                        activeState['calcHistory'] = hist;
                        renderCurrentScreen();
                    }

                    // Node Renderer
                    function renderNode(node) {
                        if (!node) return '';

                        if (node.conditionalExpr) {
                            const cond = node.conditionalExpr;
                            if (cond.startsWith('!') && activeState[cond.substring(1)]) return '';
                            if (!cond.startsWith('!') && !activeState[cond]) return '';
                        }

                        const childrenHtml = (node.children || []).map(renderNode).join('');

                        switch(node.type) {
                            case 'SCAFFOLD':
                                return '<div style="display:flex;flex-direction:column;height:100%;">' + childrenHtml + '</div>';

                            case 'APP_BAR':
                                const barTitle = (node.props && node.props.title) || node.label || 'App';
                                const barBg = (node.style && node.style.backgroundColor) ? ('background:' + node.style.backgroundColor + ';') : '';
                                const barColor = (node.style && node.style.textColor) ? ('color:' + node.style.textColor + ';') : '';
                                const barStyle = (barBg || barColor) ? ('style="' + barBg + barColor + '"') : '';
                                return '<div class="m3-app-bar" ' + barStyle + '>' + 
                                       '<svg style="width:20px;height:20px;cursor:pointer;fill:currentColor;" viewBox="0 0 24 24" onclick="showToast(\'Menu opened\')"><path d="M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z"/></svg>' +
                                       '<span>' + barTitle + '</span>' +
                                       '</div>';

                            case 'COLUMN':
                                const isCalcContainer = node.props && node.props.isCalculator;
                                const isCentered = node.style && node.style.alignment === 'center';
                                const colClass = isCalcContainer ? 'm3-calc-container' : 'm3-column';
                                const colStyle = isCentered ? 'style="display:flex;flex-direction:column;align-items:center;justify-content:center;height:100%;text-align:center;padding:24px 16px;"' : '';
                                return '<div class="' + colClass + '" ' + colStyle + '>' + childrenHtml + '</div>';

                            case 'ROW':
                                return '<div class="m3-row" style="width:100%;justify-content:space-between;">' + childrenHtml + '</div>';

                            case 'GRID':
                                const cols = node.props && node.props.columns ? node.props.columns : 4;
                                return '<div class="m3-grid" style="grid-template-columns: repeat(' + cols + ', 1fr);">' + childrenHtml + '</div>';

                            case 'CARD':
                                const cardBg = node.style && node.style.backgroundColor ? ('background-color:' + node.style.backgroundColor + ';') : '';
                                const cardColor = node.style && node.style.textColor ? ('color:' + node.style.textColor + ';') : '';
                                const cardRadius = node.style && node.style.borderRadius ? ('border-radius:' + node.style.borderRadius + ';') : '';
                                const cardPadding = node.style && node.style.padding ? ('padding:' + node.style.padding + ';') : '';
                                const cardStyle = 'style="' + cardBg + cardColor + cardRadius + cardPadding + '"';
                                return '<div class="m3-card" ' + cardStyle + '>' + childrenHtml + '</div>';

                            case 'TEXT':
                                let textVal = node.label || '';
                                if (node.stateBindings && node.stateBindings.text) {
                                    const varKey = node.stateBindings.text;
                                    textVal = activeState[varKey] !== undefined ? activeState[varKey] : textVal;
                                }
                                if (node.props && node.props.isCalcDisplay) {
                                    const disp = activeState['calcDisplay'] || '0';
                                    const hist = activeState['calcHistory'] || '';
                                    return '<div class="m3-calc-display">' +
                                           '<div class="m3-calc-history">' + hist + '</div>' +
                                           '<div class="m3-calc-val">' + disp + '</div>' +
                                           '</div>';
                                }
                                const fSize = (node.style && node.style.fontSize) ? node.style.fontSize : '15px';
                                const fWeight = (node.style && node.style.fontWeight) ? node.style.fontWeight : '400';
                                const txtColor = (node.style && node.style.textColor) ? ('color:' + node.style.textColor + ';') : '';
                                const txtAlign = (node.style && node.style.alignment === 'center') ? 'text-align:center;width:100%;' : '';
                                const txtMargin = (node.style && node.style.margin) ? ('margin:' + node.style.margin + ';') : '';
                                return '<div class="m3-text" style="font-size:' + fSize + ';font-weight:' + fWeight + ';' + txtColor + txtAlign + txtMargin + '">' + textVal + '</div>';

                            case 'BUTTON':
                                const isCalc = node.props && node.props.isCalcKey;
                                const keyVal = node.label || '0';
                                if (isCalc) {
                                    const isOp = ['+', '-', '×', '÷', '*', '/', '='].includes(keyVal);
                                    const isFn = ['C', 'AC', '±', '%', '⌫'].includes(keyVal);
                                    const isWide = keyVal === '0' && node.props.isWide;
                                    const btnClass = 'm3-calc-btn ' + (isOp ? 'op' : (isFn ? 'fn' : 'num')) + (isWide ? ' wide' : '');
                                    return '<button class="' + btnClass + '" onclick="onCalculatorInput(\'' + keyVal + '\')">' + keyVal + '</button>';
                                }
                                const customBtnBg = node.style && node.style.backgroundColor ? ('background-color:' + node.style.backgroundColor + ';') : '';
                                const customBtnColor = node.style && node.style.textColor ? ('color:' + node.style.textColor + ';') : '';
                                const customBtnRadius = node.style && node.style.borderRadius ? ('border-radius:' + node.style.borderRadius + ';') : '';
                                const customBtnPadding = node.style && node.style.padding ? ('padding:' + node.style.padding + ';') : '';
                                const customBtnStyle = 'style="' + customBtnBg + customBtnColor + customBtnRadius + customBtnPadding + '"';
                                const btnAction = (node.actions && node.actions[0]) ? getActionJs(node.actions[0]) : "showToast('Clicked " + keyVal + "')";
                                return '<button class="m3-button" ' + customBtnStyle + ' onclick="' + btnAction + '">' + keyVal + '</button>';

                            case 'OUTLINED_BUTTON':
                                const obtnAction = (node.actions && node.actions[0]) ? getActionJs(node.actions[0]) : "showToast('Clicked " + (node.label || 'Button') + "')";
                                return '<button class="m3-outlined-button" onclick="' + obtnAction + '">' + (node.label || 'Action') + '</button>';

                            case 'TEXT_FIELD':
                            case 'OUTLINED_TEXT_FIELD':
                                const valBinding = node.stateBindings && node.stateBindings.value ? node.stateBindings.value : '';
                                const curVal = valBinding && activeState[valBinding] !== undefined ? activeState[valBinding] : '';
                                const ph = node.props && node.props.placeholder ? node.props.placeholder : (node.label || 'Enter text...');
                                const onChangeJs = valBinding ? "oninput=\"setState('" + valBinding + "', this.value)\"" : "";
                                return '<input type="text" class="m3-text-field" placeholder="' + ph + '" value="' + curVal + '" ' + onChangeJs + ' />';

                            case 'SWITCH':
                                const swBinding = node.stateBindings && node.stateBindings.checked ? node.stateBindings.checked : 'isChecked';
                                const isChecked = activeState[swBinding] ? 'checked' : '';
                                return '<label class="m3-switch"><input type="checkbox" ' + isChecked + ' onchange="toggleState(\'' + swBinding + '\')"><span class="slider"></span></label>';

                            case 'FLOATING_ACTION_BUTTON':
                                const fabAction = (node.actions && node.actions[0]) ? getActionJs(node.actions[0]) : "showToast('FAB Clicked')";
                                const fabBg = (node.style && node.style.backgroundColor) ? ('background:' + node.style.backgroundColor + ';') : '';
                                const fabColor = (node.style && node.style.textColor) ? ('color:' + node.style.textColor + ';') : '';
                                const fabStyle = (fabBg || fabColor) ? ('style="' + fabBg + fabColor + '"') : '';
                                return '<button class="m3-fab" ' + fabStyle + ' onclick="' + fabAction + '"><svg style="width:26px;height:26px;fill:currentColor;" viewBox="0 0 24 24"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg></button>';

                            case 'PROGRESS_INDICATOR':
                                return '<div class="m3-progress-spinner"></div>';

                            case 'SPACER':
                                const spHeight = node.style && node.style.height ? node.style.height : '16px';
                                return '<div style="height:' + spHeight + ';"></div>';

                            case 'ICON':
                                return '<svg style="width:22px;height:22px;fill:var(--md-sys-color-primary);" viewBox="0 0 24 24"><path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/></svg>';

                            default:
                                return childrenHtml;
                        }
                    }

                    function getActionJs(action) {
                        if (!action) return '';
                        switch(action.actionType) {
                            case 'CALCULATOR_INPUT': return "onCalculatorInput('" + (action.payload || action.target) + "')";
                            case 'INCREMENT_STATE': return "incrementState('" + action.target + "')";
                            case 'TOGGLE_STATE': return "toggleState('" + action.target + "')";
                            case 'SET_STATE': return "setState('" + action.target + "', " + JSON.stringify(action.payload || true) + ")";
                            case 'NAVIGATE': return "navigateTo('" + action.target + "')";
                            case 'SHOW_TOAST':
                            case 'SHOW_SNACKBAR': return "showToast('" + action.target + "')";
                            default: return "showToast('Action executed')";
                        }
                    }

                    function renderCurrentScreen() {
                        const appRoot = document.getElementById('app-root');
                        if (!appRoot) return;

                        const screen = screensData.find(s => s.id === currentScreenId) || screensData[0];
                        if (!screen || !screen.rootNode) {
                            appRoot.innerHTML = '<div style="padding:24px;text-align:center;">No UI loaded</div>';
                            return;
                        }

                        appRoot.innerHTML = renderNode(screen.rootNode);
                    }

                    initScreen(currentScreenId);
                } catch (e) {
                    console.error('Universal Preview Render Error:', e);
                    document.getElementById('app-root').innerHTML = '<div style="padding:20px;color:#c00;">Preview rendering failed: ' + e.message + '</div>';
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    private fun serializeScreensToJson(screens: List<PreviewScreen>): String {
        val sb = StringBuilder("[")
        screens.forEachIndexed { i, screen ->
            if (i > 0) sb.append(",")
            sb.append("{")
            sb.append("\"id\":\"${screen.id}\",")
            sb.append("\"name\":\"${escapeJson(screen.name)}\",")
            sb.append("\"stateVariables\":{")
            var firstVar = true
            screen.stateVariables.forEach { (k, v) ->
                if (!firstVar) sb.append(",")
                firstVar = false
                val valStr = when (v) {
                    is Boolean -> "$v"
                    is Number -> "$v"
                    else -> "\"${escapeJson(v.toString())}\""
                }
                sb.append("\"${escapeJson(k)}\":$valStr")
            }
            sb.append("},")
            sb.append("\"rootNode\":").append(serializeNodeToJson(screen.rootNode))
            sb.append("}")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun serializeNodeToJson(node: PreviewNode): String {
        val sb = StringBuilder("{")
        sb.append("\"id\":\"${node.id}\",")
        sb.append("\"type\":\"${node.type.name}\",")
        sb.append("\"label\":\"${escapeJson(node.label)}\",")
        sb.append("\"props\":{")
        var firstProp = true
        node.props.forEach { (k, v) ->
            if (!firstProp) sb.append(",")
            firstProp = false
            val vStr = escapeJson(v.toString())
            sb.append("\"${escapeJson(k)}\":\"$vStr\"")
        }
        sb.append("},")
        sb.append("\"style\":{")
        sb.append("\"fontSize\":\"${node.style.fontSize ?: ""}\",")
        sb.append("\"fontWeight\":\"${node.style.fontWeight ?: ""}\",")
        sb.append("\"height\":\"${node.style.height ?: ""}\",")
        sb.append("\"textColor\":\"${node.style.textColor ?: ""}\",")
        sb.append("\"backgroundColor\":\"${node.style.backgroundColor ?: ""}\",")
        sb.append("\"borderRadius\":\"${node.style.borderRadius ?: ""}\",")
        sb.append("\"padding\":\"${node.style.padding ?: ""}\",")
        sb.append("\"alignment\":\"${node.style.alignment ?: ""}\",")
        sb.append("\"margin\":\"${node.style.margin ?: ""}\"")
        sb.append("},")
        sb.append("\"stateBindings\":{")
        var firstBind = true
        node.stateBindings.forEach { (k, v) ->
            if (!firstBind) sb.append(",")
            firstBind = false
            sb.append("\"${escapeJson(k)}\":\"${escapeJson(v)}\"")
        }
        sb.append("},")
        sb.append("\"actions\":[")
        node.actions.forEachIndexed { idx, act ->
            if (idx > 0) sb.append(",")
            sb.append("{")
            sb.append("\"actionType\":\"${act.actionType.name}\",")
            sb.append("\"target\":\"${escapeJson(act.target)}\",")
            sb.append("\"payload\":\"${escapeJson(act.payload)}\"")
            sb.append("}")
        }
        sb.append("],")
        sb.append("\"children\":[")
        node.children.forEachIndexed { idx, child ->
            if (idx > 0) sb.append(",")
            sb.append(serializeNodeToJson(child))
        }
        sb.append("]")
        sb.append("}")
        return sb.toString()
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("\t", " ")
    }

    private fun escapeHtml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
