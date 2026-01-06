-- Add amount_type column to agent_rules table
ALTER TABLE agent_rules 
ADD COLUMN amount_type VARCHAR(10) DEFAULT 'USD';

-- Add index for performance (optional but recommended)
CREATE INDEX idx_agent_rules_active ON agent_rules(is_active) WHERE is_active = true;
