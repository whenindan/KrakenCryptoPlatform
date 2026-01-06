-- Add trading mode column to users table
-- Default to PAPER mode for safety
ALTER TABLE users 
ADD COLUMN trading_mode VARCHAR(10) DEFAULT 'PAPER';

-- Create index for performance
CREATE INDEX idx_users_trading_mode ON users(trading_mode);
