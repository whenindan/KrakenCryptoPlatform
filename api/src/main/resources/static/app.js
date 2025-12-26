const CONFIG = {
    websocketUrl: `ws://${window.location.host}/ws/prices`,
    endpoints: {
        markets: '/markets',
        latest: '/prices/latest'
    }
};

class CryptoApp {
    constructor() {
        this.socket = null;
        this.markets = [];
        this.cache = {}; // Stores last prices for comparison

        this.dom = {
            marketsContainer: document.getElementById('markets-container'),
            logsContainer: document.getElementById('logs-container'),
            connectionStatus: document.getElementById('connection-status'),
            connectionDot: document.getElementById('connection-dot'),
            clearLogsBtn: document.getElementById('clear-logs')
        };

        this.init();
    }

    async init() {
        this.setupEventListeners();
        await this.fetchMarkets();
        this.renderInitialCards();
        this.connect();
    }

    setupEventListeners() {
        this.dom.clearLogsBtn.addEventListener('click', () => {
            this.dom.logsContainer.innerHTML = '';
        });
    }

    async fetchMarkets() {
        try {
            const res = await fetch(CONFIG.endpoints.markets);
            this.markets = await res.json();
            // Also fetch initial prices to populate immediately
            const pricesRes = await fetch(CONFIG.endpoints.latest);
            const prices = await pricesRes.json();
            this.cache = prices;
        } catch (e) {
            console.error('Failed to fetch markets', e);
            this.dom.marketsContainer.innerHTML = '<div class="loading-state error">Failed to load markets</div>';
        }
    }

    renderInitialCards() {
        this.dom.marketsContainer.innerHTML = ''; // Clear loading state

        this.markets.forEach(symbol => {
            const initialData = this.cache[symbol] || { last: 0, bid: 0, ask: 0, volume24h: 0 };

            const card = document.createElement('div');
            card.className = 'ticker-card';
            card.id = `card-${symbol}`;

            card.innerHTML = `
                <div class="card-header">
                    <span class="symbol-name">${symbol.replace('-', ' / ')}</span>
                    <ion-icon name="trending-up-outline"></ion-icon>
                </div>
                <div class="price-container">
                    <div class="current-price" id="price-${symbol}">${this.formatPrice(initialData.last)}</div>
                </div>
                <div class="price-details">
                    <div class="detail-item">
                        <span class="detail-label">Bid</span>
                        <span class="detail-value" id="bid-${symbol}">${this.formatPrice(initialData.bid)}</span>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">Ask</span>
                        <span class="detail-value" id="ask-${symbol}">${this.formatPrice(initialData.ask)}</span>
                    </div>
                </div>
            `;

            this.dom.marketsContainer.appendChild(card);
        });
    }

    connect() {
        this.UpdateConnectionStatus('Connecting...', 'connecting');
        this.socket = new WebSocket(CONFIG.websocketUrl);

        this.socket.onopen = () => {
            this.UpdateConnectionStatus('Connected', 'connected');
            this.log('System', 'Connected to WebSocket stream');

            // Subscribe
            const msg = {
                type: 'subscribe',
                symbols: this.markets
            };
            this.socket.send(JSON.stringify(msg));
        };

        this.socket.onmessage = (event) => {
            const data = JSON.parse(event.data);
            if (data.type === 'tick') {
                this.handleTick(data);
            } else if (data.type === 'subscribed') {
                this.log('System', `Subscribed to: ${data.symbols.join(', ')}`);
            }
        };

        this.socket.onclose = () => {
            this.UpdateConnectionStatus('Disconnected - Retrying in 5s', 'disconnected');
            this.log('System', 'Connection lost', true);
            setTimeout(() => this.connect(), 5000);
        };

        this.socket.onerror = (e) => {
            console.error('WebSocket Error', e);
        };
    }

    handleTick(data) {
        const { symbol, last, bid, ask, ts } = data;

        // Log basic tick (throttled visually? no, stream is already throttled)
        // this.log('Tick', `${symbol} @ ${last}`, false, true); // Too noisy? Maybe just occasional

        const card = document.getElementById(`card-${symbol}`);
        const priceEl = document.getElementById(`price-${symbol}`);
        const bidEl = document.getElementById(`bid-${symbol}`);
        const askEl = document.getElementById(`ask-${symbol}`);

        if (!card || !priceEl) return;

        // Compare with previous price for animation
        const prevLast = this.cache[symbol]?.last || 0;

        // Update DOM
        priceEl.innerText = this.formatPrice(last);
        bidEl.innerText = this.formatPrice(bid);
        askEl.innerText = this.formatPrice(ask);

        // Animation
        // Remove old classes to restart animation
        card.classList.remove('flash-bg-up', 'flash-bg-down');
        priceEl.classList.remove('up', 'down');

        // Trigger reflow
        void card.offsetWidth;

        if (last > prevLast) {
            card.classList.add('flash-bg-up');
            priceEl.classList.add('up');
            this.log('Tick', `${symbol} ▲ ${last}`, false, true);
        } else if (last < prevLast) {
            card.classList.add('flash-bg-down');
            priceEl.classList.add('down');
            this.log('Tick', `${symbol} ▼ ${last}`, false, true);
        }

        // Update cache
        this.cache[symbol] = { last, bid, ask };
    }

    UpdateConnectionStatus(text, state) {
        this.dom.connectionStatus.innerText = text;
        this.dom.connectionDot.className = 'status-dot ' + state;
    }

    log(source, message, isError = false, isTick = false) {
        const entry = document.createElement('div');
        entry.className = `log-entry ${isTick ? 'tick' : ''}`;

        const time = new Date().toLocaleTimeString();
        entry.innerHTML = `
            <span class="log-ts">[${time}]</span>
            <span class="log-msg ${isTick ? 'tick' : ''}">${source}: ${message}</span>
        `;

        this.dom.logsContainer.prepend(entry);

        // Limit logs to 50
        if (this.dom.logsContainer.children.length > 50) {
            this.dom.logsContainer.lastElementChild.remove();
        }
    }

    formatPrice(price) {
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }).format(price);
    }
}

// Start App
document.addEventListener('DOMContentLoaded', () => {
    new CryptoApp();
});
