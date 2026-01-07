const CONFIG = {
    websocketUrl: `ws://${window.location.host}/ws/prices`,
    endpoints: {
        markets: '/markets',
        latest: '/prices/latest',
        signup: '/auth/signup',
        login: '/auth/login',
        balance: '/account/balance',
        orders: '/trade/orders',
        portfolio: '/trade/portfolio',
        aiCommand: '/ai/command',
        aiRules: '/ai/rules',
        aiConfirm: '/ai/confirm/{id}',
        tradingMode: '/account/trading-mode'
    }
};

class CryptoApp {
    constructor() {
        this.socket = null;
        this.markets = [];
        this.cache = {}; // Price cache
        this.token = localStorage.getItem('jwt_token') || null;

        this.state = {
            selectedSymbol: 'BTC-USD',
            side: 'BUY',
            type: 'MARKET',
            orders: [],
            portfolio: []
        };

        this.dom = {
            // Panels
            marketsContainer: document.getElementById('markets-container'),
            logsContainer: document.getElementById('logs-container'),
            portfolioContainer: document.getElementById('portfolio-container'),
            ordersContainer: document.getElementById('orders-container'),

            // Trading Panel
            selectedSymbol: document.getElementById('selected-symbol'),
            tabBuy: document.getElementById('tab-buy'),
            tabSell: document.getElementById('tab-sell'),
            typeMarket: document.getElementById('type-market'),
            typeLimit: document.getElementById('type-limit'),
            limitPriceGroup: document.getElementById('limit-price-group'),
            inputPrice: document.getElementById('trade-price'),
            inputQty: document.getElementById('trade-quantity'),
            qtySuffix: document.getElementById('trade-qty-suffix'),
            placeOrderBtn: document.getElementById('place-order-btn'),

            // Auth
            authSection: document.getElementById('auth-section'),
            userSection: document.getElementById('user-section'),
            emailInput: document.getElementById('email'),
            passwordInput: document.getElementById('password'),
            loginBtn: document.getElementById('login-btn'),
            signupBtn: document.getElementById('signup-btn'),
            logoutBtn: document.getElementById('logout-btn'),
            balanceSpan: document.getElementById('balance'),

            // Status
            connectionIcon: document.getElementById('connection-icon'),

            // AI Chatbox
            aiChatbox: document.getElementById('ai-chatbox'),
            aiToggleBtn: document.getElementById('ai-toggle-btn'),
            aiCloseBtn: document.getElementById('ai-close-btn'),
            aiMessages: document.getElementById('ai-messages'),
            aiInput: document.getElementById('ai-input'),
            aiSendBtn: document.getElementById('ai-send-btn'),
            aiLoginPrompt: document.getElementById('ai-login-prompt'),

            // Trading Mode
            modePaperBtn: document.getElementById('mode-paper'),
            modeLiveBtn: document.getElementById('mode-live')
        };

        this.init();
    }

    async init() {
        this.setupEventListeners();
        this.checkAuth();
        await this.fetchMarkets();
        this.renderInitialCards();
        this.connect();

        // Polling for user data
        setInterval(() => {
            if (this.token) {
                this.fetchAccountData();
            }
        }, 2000);
    }

    setupEventListeners() {
        // Auth
        this.dom.loginBtn.addEventListener('click', () => this.handleLogin());
        this.dom.signupBtn.addEventListener('click', () => this.handleSignup());
        this.dom.logoutBtn.addEventListener('click', () => this.handleLogout());

        // Trade UI
        this.dom.tabBuy.addEventListener('click', () => this.setSide('BUY'));
        this.dom.tabSell.addEventListener('click', () => this.setSide('SELL'));
        this.dom.typeMarket.addEventListener('click', () => this.setOrderType('MARKET'));
        this.dom.typeLimit.addEventListener('click', () => this.setOrderType('LIMIT'));

        this.dom.placeOrderBtn.addEventListener('click', () => this.placeOrder());

        // AI Chatbox
        this.dom.aiToggleBtn.addEventListener('click', () => this.toggleAiChat());
        this.dom.aiCloseBtn.addEventListener('click', () => this.closeAiChat());
        this.dom.aiSendBtn.addEventListener('click', () => this.sendAiMessage());
        this.dom.aiInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                if (e.shiftKey) {
                    // SHIFT+ENTER: allow newline (default behavior)
                    return;
                } else {
                    // ENTER alone: send message
                    if (!this.dom.aiInput.disabled) {
                        e.preventDefault(); // Prevent newline
                        this.sendAiMessage();
                    }
                }
            }
        });

        // Trading Mode
        this.dom.modePaperBtn.addEventListener('click', () => this.switchTradingMode('PAPER'));
        this.dom.modeLiveBtn.addEventListener('click', () => this.switchTradingMode('LIVE'));
        // Auto-resize textarea as user types
        this.dom.aiInput.addEventListener('input', () => {
            this.dom.aiInput.style.height = 'auto';
            this.dom.aiInput.style.height = Math.min(this.dom.aiInput.scrollHeight, 120) + 'px';
        });
    }

    // --- Auth Logic ---

    checkAuth() {
        if (this.token) {
            this.dom.authSection.style.display = 'none';
            this.dom.userSection.style.display = 'flex';
            this.fetchAccountData();
            // Enable AI chat
            this.dom.aiInput.disabled = false;
            this.dom.aiSendBtn.disabled = false;
            this.dom.aiLoginPrompt.classList.add('hidden');
        } else {
            this.dom.authSection.style.display = 'block';
            this.dom.userSection.style.display = 'none';
            this.dom.portfolioContainer.innerHTML = '<div style="text-align: center; color: var(--text-muted); padding: 1rem;">Login to view assets</div>';
            // Disable AI chat
            this.dom.aiInput.disabled = true;
            this.dom.aiSendBtn.disabled = true;
            this.dom.aiLoginPrompt.classList.remove('hidden');
        }
    }

    async handleLogin() {
        const email = this.dom.emailInput.value;
        const password = this.dom.passwordInput.value;
        try {
            const res = await fetch(CONFIG.endpoints.login, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, password })
            });
            if (res.ok) {
                const data = await res.json();
                this.token = data.token;
                localStorage.setItem('jwt_token', this.token);
                this.checkAuth();
                this.log('Auth', 'Logged in successfully');
            } else {
                alert('Login failed');
            }
        } catch (e) {
            console.error(e);
        }
    }

    async handleSignup() {
        const email = this.dom.emailInput.value;
        const password = this.dom.passwordInput.value;
        try {
            const res = await fetch(CONFIG.endpoints.signup, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, password })
            });
            if (res.ok) {
                alert('Signup successful');
            } else {
                alert('Signup failed');
            }
        } catch (e) { console.error(e); }
    }

    handleLogout() {
        this.token = null;
        localStorage.removeItem('jwt_token');
        this.checkAuth();
        this.dom.emailInput.value = '';
        this.dom.passwordInput.value = '';
    }

    async fetchAccountData() {
        if (!this.token) return;
        const headers = { 'Authorization': 'Bearer ' + this.token };

        // Balance
        try {
            const bRes = await fetch(CONFIG.endpoints.balance, { headers });
            if (bRes.ok) {
                const bData = await bRes.json();
                this.dom.balanceSpan.innerText = this.formatPrice(bData.balance).replace('$', '');
            }

            // Portfolio
            const pRes = await fetch(CONFIG.endpoints.portfolio, { headers });
            if (pRes.ok) {
                this.state.portfolio = await pRes.json();
                this.renderPortfolio();
            }

            // Orders
            const oRes = await fetch(CONFIG.endpoints.orders, { headers });
            if (oRes.ok) {
                this.state.orders = await oRes.json();
                this.renderOrders();
            }

            // Trading Mode
            this.fetchTradingMode();
        } catch (e) { console.error(e); }
    }

    // --- Trading Mode Management ---

    async fetchTradingMode() {
        if (!this.token) return;

        try {
            const res = await fetch(CONFIG.endpoints.tradingMode, {
                headers: { 'Authorization': `Bearer ${this.token}` }
            });

            if (res.ok) {
                const data = await res.json();
                this.updateTradingModeUI(data.mode, data.krakenConnected);
                this.showLiveModeWarning(data.mode === 'LIVE');
            }
        } catch (e) {
            console.error('Failed to fetch trading mode', e);
        }
    }

    async switchTradingMode(mode) {
        if (!this.token) return;

        // Safety confirmation for switching to LIVE
        if (mode === 'LIVE') {
            const confirmed = confirm(
                '⚠️ WARNING: LIVE TRADING MODE\n\n' +
                'You are about to switch to LIVE trading mode.\n' +
                'ALL trades will execute with REAL MONEY on Kraken!\n\n' +
                'Are you absolutely sure you want to continue?'
            );

            if (!confirmed) {
                return;
            }
        }

        try {
            const res = await fetch(CONFIG.endpoints.tradingMode, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${this.token}`,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ mode })
            });

            const data = await res.json();

            if (res.ok && data.mode === mode) {
                this.updateTradingModeUI(data.mode, data.krakenConnected);
                this.showLiveModeWarning(data.mode === 'LIVE');
                this.log('Trading Mode', data.message);
            } else {
                alert('Failed to switch mode: ' + data.message);
            }
        } catch (e) {
            console.error('Failed to switch trading mode', e);
            alert('Error switching trading mode. Please try again.');
        }
    }

    updateTradingModeUI(mode, krakenConnected) {
        // Update button states
        if (mode === 'PAPER') {
            this.dom.modePaperBtn.classList.add('active');
            this.dom.modeLiveBtn.classList.remove('active');
        } else {
            this.dom.modeLiveBtn.classList.add('active');
            this.dom.modePaperBtn.classList.remove('active');
        }

        // Update button text to show connection status if LIVE
        if (mode === 'LIVE') {
            const icon = krakenConnected ? '💰' : '⚠️';
            this.dom.modeLiveBtn.innerHTML = `${icon} Live`;
        } else {
            this.dom.modeLiveBtn.innerHTML = '💰 Live';
        }
    }

    showLiveModeWarning(show) {
        let banner = document.getElementById('live-mode-banner');

        if (show && !banner) {
            // Create warning banner
            banner = document.createElement('div');
            banner.id = 'live-mode-banner';
            banner.className = 'live-mode-warning';
            banner.innerHTML = `
                <ion-icon name="warning"></ion-icon>
                <div class="live-mode-warning-text">
                    <strong>⚠️ LIVE TRADING MODE ACTIVE</strong><br>
                    All trades execute with real money on Kraken
                </div>
            `;

            // Insert at top of main content
            const main = document.querySelector('main');
            main.insertBefore(banner, main.firstChild);
        } else if (!show && banner) {
            // Remove banner
            banner.remove();
        }
    }

    // --- Trading Logic ---

    setSide(side) {
        this.state.side = side;
        if (side === 'BUY') {
            this.dom.tabBuy.classList.add('active');
            this.dom.tabSell.classList.remove('active');
            this.dom.placeOrderBtn.classList.remove('sell');
            this.dom.placeOrderBtn.classList.add('buy');
            this.dom.placeOrderBtn.innerText = `Buy ${this.state.selectedSymbol.split('-')[0]}`;
        } else {
            this.dom.tabSell.classList.add('active');
            this.dom.tabBuy.classList.remove('active');
            this.dom.placeOrderBtn.classList.remove('buy');
            this.dom.placeOrderBtn.classList.add('sell');
            this.dom.placeOrderBtn.innerText = `Sell ${this.state.selectedSymbol.split('-')[0]}`;
        }
    }

    setOrderType(type) {
        this.state.type = type;
        if (type === 'MARKET') {
            this.dom.typeMarket.classList.add('active');
            this.dom.typeLimit.classList.remove('active');
            this.dom.limitPriceGroup.style.display = 'none';
        } else {
            this.dom.typeLimit.classList.add('active');
            this.dom.typeMarket.classList.remove('active');
            this.dom.limitPriceGroup.style.display = 'flex';
        }
    }

    selectSymbol(symbol) {
        // Deselect prev
        const prevCard = document.getElementById(`card-${this.state.selectedSymbol}`);
        if (prevCard) prevCard.classList.remove('selected');

        this.state.selectedSymbol = symbol;
        const newCard = document.getElementById(`card-${symbol}`);
        if (newCard) newCard.classList.add('selected');

        this.dom.selectedSymbol.innerText = symbol;
        this.dom.qtySuffix.innerText = symbol.split('-')[0];

        // Update price input default if limit
        const price = this.cache[symbol]?.last || 0;
        this.dom.inputPrice.value = price;
        this.setSide(this.state.side); // Refresh button text
    }

    async placeOrder() {
        if (!this.token) {
            alert('Please login first');
            return;
        }

        const qty = parseFloat(this.dom.inputQty.value);
        if (!qty || qty <= 0) {
            alert('Invalid quantity');
            return;
        }

        const payload = {
            symbol: this.state.selectedSymbol,
            side: this.state.side,
            type: this.state.type,
            quantity: qty
        };

        if (this.state.type === 'LIMIT') {
            const price = parseFloat(this.dom.inputPrice.value);
            if (!price || price <= 0) {
                alert('Invalid price');
                return;
            }
            payload.limitPrice = price;
        }

        try {
            const res = await fetch(CONFIG.endpoints.orders, {
                method: 'POST',
                headers: {
                    'Authorization': 'Bearer ' + this.token,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload)
            });

            if (res.ok) {
                const order = await res.json();
                this.log('Trade', `Order Placed: ${this.state.side} ${qty} ${this.state.selectedSymbol}`);
                this.fetchAccountData(); // Refresh immediately
                // Clear inputs
                this.dom.inputQty.value = '';
            } else {
                const txt = await res.text(); // Probably json error
                alert('Order Failed: ' + txt);
            }
        } catch (e) {
            alert('Order Error');
            console.error(e);
        }
    }

    // --- Market Data ---

    async fetchMarkets() {
        try {
            const res = await fetch(CONFIG.endpoints.markets);
            this.markets = await res.json();
            const pricesRes = await fetch(CONFIG.endpoints.latest);
            this.cache = await pricesRes.json();

            // Set initial selection
            if (this.markets.length > 0) this.state.selectedSymbol = this.markets[0];
        } catch (e) {
            console.error('Failed to fetch markets');
        }
    }

    renderInitialCards() {
        this.dom.marketsContainer.innerHTML = '';
        this.markets.forEach(symbol => {
            const initialData = this.cache[symbol] || { last: 0, change24h: 0 };

            const card = document.createElement('div');
            card.className = 'ticker-card';
            if (symbol === this.state.selectedSymbol) card.classList.add('selected');
            card.id = `card-${symbol}`;
            card.onclick = () => this.selectSymbol(symbol);

            card.innerHTML = `
                <div class="card-top">
                    <span class="asset-name">${symbol.replace('-USD', '')}</span>
                    <span class="card-price" id="price-${symbol}">${this.formatPrice(initialData.last)}</span>
                </div>
                <div class="card-meta">
                    <span>Vol: --</span>
                    <span id="change-${symbol}" class="${initialData.change24h >= 0 ? 'up' : 'down'}">
                        ${initialData.change24h}%
                    </span>
                </div>
            `;
            this.dom.marketsContainer.appendChild(card);
        });
        this.dom.qtySuffix.innerText = this.state.selectedSymbol.split('-')[0];
    }

    renderPortfolio() {
        if (this.state.portfolio.length === 0) {
            this.dom.portfolioContainer.innerHTML = '<div style="padding:1rem;color:var(--text-muted);text-align:center">No assets</div>';
            return;
        }

        this.dom.portfolioContainer.innerHTML = this.state.portfolio.map(p => `
            <div class="portfolio-item">
                <span class="p-symbol">${p.symbol}</span>
                <span class="p-qty">${p.quantity}</span>
            </div>
        `).join('');
    }

    renderOrders() {
        // Filter for open orders
        const openOrders = this.state.orders.filter(o => o.status === 'OPEN');
        if (openOrders.length === 0) {
            this.dom.ordersContainer.innerHTML = '<div style="padding:1rem;color:var(--text-muted);text-align:center">No open orders</div>';
            return;
        }

        this.dom.ordersContainer.innerHTML = openOrders.map(o => `
            <div class="portfolio-item">
                <span class="p-symbol">${o.side} ${o.symbol}</span>
                <span class="p-qty">@ ${o.limitPrice}</span>
            </div>
        `).join('');
    }

    connect() {
        this.socket = new WebSocket(CONFIG.websocketUrl);
        this.socket.onopen = () => {
            this.dom.connectionIcon.style.color = 'var(--success-color)';
            this.socket.send(JSON.stringify({ type: 'subscribe', symbols: this.markets }));
        };

        this.socket.onmessage = (event) => {
            const data = JSON.parse(event.data);
            if (data.type === 'tick') this.handleTick(data);
        };

        this.socket.onclose = () => {
            this.dom.connectionIcon.style.color = 'var(--danger-color)';
            setTimeout(() => this.connect(), 3000);
        };
    }

    handleTick(data) {
        const { symbol, last, change24h } = data;
        this.cache[symbol] = { last, change24h };

        // Update DOM
        const priceEl = document.getElementById(`price-${symbol}`);
        const changeEl = document.getElementById(`change-${symbol}`);

        if (priceEl) {
            priceEl.innerText = this.formatPrice(last);
            // Flash effect?
            // Simple color toggle
            priceEl.style.color = 'var(--text-primary)';
            // We could add complex flash, but minimalistic is fine for premium feel
        }
        if (changeEl) {
            changeEl.innerText = change24h + '%';
            changeEl.className = change24h >= 0 ? 'up' : 'down';
        }
    }

    log(source, message) {
        const line = document.createElement('div');
        line.className = 'log-line';
        line.innerHTML = `<span>${source}:</span> ${message}`;
        this.dom.logsContainer.prepend(line);
    }

    formatPrice(price) {
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 2
        }).format(price);
    }

    // --- AI Chatbox Methods ---

    toggleAiChat() {
        this.dom.aiChatbox.classList.toggle('open');
    }

    closeAiChat() {
        this.dom.aiChatbox.classList.remove('open');
    }

    addAiMessage(text, isUser = false, isSuccess = false, isError = false) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `ai-message ${isUser ? 'user' : 'bot'}`;

        const avatar = document.createElement('div');
        avatar.className = 'ai-avatar';
        avatar.textContent = isUser ? '👤' : '🤖';

        const bubble = document.createElement('div');
        bubble.className = 'ai-bubble';

        const p = document.createElement('p');
        p.textContent = text;
        bubble.appendChild(p);

        if (isSuccess) {
            const tag = document.createElement('div');
            tag.className = 'success-tag';
            tag.innerHTML = '<ion-icon name="checkmark-circle"></ion-icon> Success';
            bubble.appendChild(tag);
        }

        if (isError) {
            const tag = document.createElement('div');
            tag.className = 'error-tag';
            tag.innerHTML = '<ion-icon name="alert-circle"></ion-icon> Error';
            bubble.appendChild(tag);
        }

        messageDiv.appendChild(avatar);
        messageDiv.appendChild(bubble);

        this.dom.aiMessages.appendChild(messageDiv);
        this.dom.aiMessages.scrollTop = this.dom.aiMessages.scrollHeight;
    }

    showTypingIndicator() {
        const typingDiv = document.createElement('div');
        typingDiv.className = 'ai-message bot';
        typingDiv.id = 'ai-typing';

        const avatar = document.createElement('div');
        avatar.className = 'ai-avatar';
        avatar.textContent = '🤖';

        const bubble = document.createElement('div');
        bubble.className = 'ai-bubble';
        bubble.innerHTML = '<div class="ai-typing"><div class="ai-typing-dot"></div><div class="ai-typing-dot"></div><div class="ai-typing-dot"></div></div>';

        typingDiv.appendChild(avatar);
        typingDiv.appendChild(bubble);

        this.dom.aiMessages.appendChild(typingDiv);
        this.dom.aiMessages.scrollTop = this.dom.aiMessages.scrollHeight;
    }

    removeTypingIndicator() {
        const typingIndicator = document.getElementById('ai-typing');
        if (typingIndicator) {
            typingIndicator.remove();
        }
    }

    async sendAiMessage() {
        const message = this.dom.aiInput.value.trim();
        if (!message || !this.token) return;

        // Add user message
        this.addAiMessage(message, true);
        this.dom.aiInput.value = '';
        this.dom.aiInput.style.height = 'auto'; // Reset height after sending

        // Show typing indicator
        this.showTypingIndicator();

        try {
            const res = await fetch(CONFIG.endpoints.aiCommand, {
                method: 'POST',
                headers: {
                    'Authorization': 'Bearer ' + this.token,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ command: message })
            });

            this.removeTypingIndicator();

            if (res.ok) {
                const data = await res.json();
                console.log('AI Response:', data); // Debug logging

                if (data.success) {
                    // Check if response requires confirmation
                    if (data.requiresConfirmation) {
                        const confirmMsg = data.message || data.confirmationMessage;
                        if (confirmMsg && data.confirmationId) {
                            this.addAiConfirmationMessage(confirmMsg, data.confirmationId);
                        } else {
                            console.error('Missing confirmation data:', data);
                            this.addAiMessage('I understood your command, but there was an issue displaying the confirmation. Please try again.', false, false, true);
                        }
                    } else {
                        // Regular success message (no confirmation needed)
                        this.addAiMessage(data.message, false, true);
                        // Refresh account data to show updated balance/orders
                        this.fetchAccountData();
                        // Log to terminal
                        this.log('AI Agent', data.message);
                    }
                } else {
                    // Only show error box when success is explicitly false
                    this.addAiMessage(data.message || 'Command failed', false, false, true);
                }
            } else {
                const errorText = await res.text();
                this.addAiMessage('Failed to process command: ' + errorText, false, false, true);
            }
        } catch (e) {
            this.removeTypingIndicator();
            this.addAiMessage('Error communicating with AI agent', false, false, true);
            console.error(e);
        }
    }

    addAiConfirmationMessage(message, confirmationId) {
        try {
            if (!message || !confirmationId) {
                console.error('Invalid confirmation data - message:', message, 'id:', confirmationId);
                this.addAiMessage('I understood your command, but could not create the confirmation. Please try again.', false, false, true);
                return;
            }

            const messageDiv = document.createElement('div');
            messageDiv.classList.add('ai-message', 'bot');

            const avatar = document.createElement('div');
            avatar.classList.add('ai-avatar');
            avatar.textContent = '🤖';

            const bubble = document.createElement('div');
            bubble.classList.add('ai-bubble');

            const text = document.createElement('p');
            text.textContent = message;
            bubble.appendChild(text);

            const buttonContainer = document.createElement('div');
            buttonContainer.style.cssText = 'display: flex; gap: 0.5rem; margin-top: 0.75rem;';

            const confirmBtn = document.createElement('button');
            confirmBtn.textContent = 'Confirm';
            confirmBtn.style.cssText = 'flex: 1; padding: 0.5rem 1rem; background: linear-gradient(135deg, var(--success-color), #059669); color: white; border: none; border-radius: 8px; font-weight: 600; cursor: pointer; transition: transform 0.2s;';
            confirmBtn.addEventListener('mouseenter', () => confirmBtn.style.transform = 'translateY(-2px)');
            confirmBtn.addEventListener('mouseleave', () => confirmBtn.style.transform = 'translateY(0)');
            confirmBtn.addEventListener('click', () => this.handleConfirmation(confirmationId, true, messageDiv));

            const declineBtn = document.createElement('button');
            declineBtn.textContent = 'Decline';
            declineBtn.style.cssText = 'flex: 1; padding: 0.5rem 1rem; background: rgba(255, 255, 255, 0.1); color: var(--text-secondary); border: 1px solid var(--card-border); border-radius: 8px; font-weight: 600; cursor: pointer; transition: all 0.2s;';
            declineBtn.addEventListener('mouseenter', () => {
                declineBtn.style.background = 'rgba(255, 255, 255, 0.15)';
                declineBtn.style.color = 'var(--text-primary)';
            });
            declineBtn.addEventListener('mouseleave', () => {
                declineBtn.style.background = 'rgba(255, 255, 255, 0.1)';
                declineBtn.style.color = 'var(--text-secondary)';
            });
            declineBtn.addEventListener('click', () => this.handleConfirmation(confirmationId, false, messageDiv));

            buttonContainer.appendChild(confirmBtn);
            buttonContainer.appendChild(declineBtn);
            bubble.appendChild(buttonContainer);

            messageDiv.appendChild(avatar);
            messageDiv.appendChild(bubble);

            this.dom.aiMessages.appendChild(messageDiv);
            this.dom.aiMessages.scrollTop = this.dom.aiMessages.scrollHeight;
        } catch (error) {
            console.error('Error creating confirmation message:', error);
            this.addAiMessage('Sorry, there was an error displaying the confirmation. Please try your command again.', false, false, true);
        }
    }

    async handleConfirmation(confirmationId, confirmed, messageDiv) {
        try {
            // Disable buttons
            const buttons = messageDiv.querySelectorAll('button');
            buttons.forEach(btn => btn.disabled = true);

            if (confirmed) {
                this.showTypingIndicator();

                const res = await fetch(CONFIG.endpoints.aiConfirm.replace('{id}', confirmationId), {
                    method: 'POST',
                    headers: { 'Authorization': 'Bearer ' + this.token }
                });

                this.removeTypingIndicator();

                if (res.ok) {
                    const data = await res.json();
                    this.addAiMessage(data.message, false, data.success);

                    // Refresh account data
                    this.fetchAccountData();
                    this.log('AI Agent', data.message);
                } else {
                    this.addAiMessage('Failed to execute command', false, false, true);
                }
            } else {
                // Cancel confirmation
                await fetch(CONFIG.endpoints.aiConfirm.replace('{id}', confirmationId), {
                    method: 'DELETE',
                    headers: { 'Authorization': 'Bearer ' + this.token }
                });

                this.addAiMessage('Command canceled. Feel free to try again with a different request!', false);
            }
        } catch (e) {
            console.error(e);
            this.addAiMessage('Error processing confirmation', false, false, true);
        }
    }
}

document.addEventListener('DOMContentLoaded', () => {
    new CryptoApp();
});
