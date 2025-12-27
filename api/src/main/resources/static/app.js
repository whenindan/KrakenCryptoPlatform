const CONFIG = {
    websocketUrl: `ws://${window.location.host}/ws/prices`,
    endpoints: {
        markets: '/markets',
        latest: '/prices/latest',
        signup: '/auth/signup',
        login: '/auth/login',
        balance: '/account/balance',
        orders: '/trade/orders',
        portfolio: '/trade/portfolio'
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
            connectionIcon: document.getElementById('connection-icon')
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
    }

    // --- Auth Logic ---

    checkAuth() {
        if (this.token) {
            this.dom.authSection.style.display = 'none';
            this.dom.userSection.style.display = 'flex';
            this.fetchAccountData();
        } else {
            this.dom.authSection.style.display = 'block';
            this.dom.userSection.style.display = 'none';
            this.dom.portfolioContainer.innerHTML = '<div style="text-align: center; color: var(--text-muted); padding: 1rem;">Login to view assets</div>';
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
        } catch (e) { console.error(e); }
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
}

document.addEventListener('DOMContentLoaded', () => {
    new CryptoApp();
});
