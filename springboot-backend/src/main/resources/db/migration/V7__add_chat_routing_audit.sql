-- 兼容旧版 Hibernate 自动建表数据库：旧库首次接入 Flyway 并 baseline 到 V1 时，
-- 可能缺少早期业务表。全新数据库已由 V1 创建，下面语句会安全跳过。
create table if not exists chat_message (
    id binary(16) not null,
    session_id binary(16) not null,
    role varchar(255) not null,
    content text not null,
    sources_json text,
    retrieval_mode varchar(255),
    rerank_elapsed_seconds float(53),
    created_at timestamp(6) not null,
    primary key (id)
);

alter table chat_message
    add column routing_decision_json text;
