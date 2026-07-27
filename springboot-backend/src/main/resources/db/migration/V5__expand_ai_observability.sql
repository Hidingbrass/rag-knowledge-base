alter table ai_call_log
    add column model_names varchar(500);

alter table ai_call_log
    add column upstream_call_count integer not null default 0;

alter table ai_call_log
    add column retry_count integer not null default 0;

alter table ai_call_log
    add column prompt_tokens bigint not null default 0;

alter table ai_call_log
    add column completion_tokens bigint not null default 0;

alter table ai_call_log
    add column total_tokens bigint not null default 0;

alter table ai_call_log
    add column estimated_cost_yuan decimal(18, 6) not null default 0;
