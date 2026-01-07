-- Add kraken_txid column for tracking Kraken transactions
ALTER TABLE orders 
ADD COLUMN kraken_txid VARCHAR(255);

-- Index for quick lookup by Kraken transaction ID
CREATE INDEX idx_orders_kraken_txid ON orders(kraken_txid);
