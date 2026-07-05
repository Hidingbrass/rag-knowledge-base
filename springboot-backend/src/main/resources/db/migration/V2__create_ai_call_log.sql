create table ai_call_log (
    id binary(16) not null,
    provider varchar(255) not null,
    business_type varchar(255) not null,
    endpoint varchar(255) not null,
    success boolean not null,
    elapsed_ms bigint not null,
    error_message text,
    created_at timestamp(6) not null,
    primary key (id)
);

create index idx_ai_call_log_business_created_at
    on ai_call_log (business_type, created_at);

create index idx_ai_call_log_success_created_at
    on ai_call_log (success, created_at);
