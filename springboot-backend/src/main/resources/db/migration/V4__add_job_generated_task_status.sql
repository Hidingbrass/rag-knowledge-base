-- 兼容旧版 Hibernate 自动建表数据库。
-- 旧库首次接入 Flyway 时会 baseline 到 V1，但它可能还没有后期新增的求职业务表。
-- 全新数据库已经由 V1 创建这些表，下面的 create table if not exists 会安全跳过。
create table if not exists job_generated_task (
    id binary(16) not null,
    user_id varchar(255) not null,
    task_type varchar(255) not null,
    resume_text text not null,
    job_description text not null,
    result_json text not null,
    created_at timestamp(6) not null,
    primary key (id)
);

create table if not exists job_favorite (
    id binary(16) not null,
    user_id varchar(255) not null,
    job_title varchar(255) not null,
    company_name varchar(255) not null,
    job_description text not null,
    source_url varchar(255),
    notes text,
    created_at timestamp(6) not null,
    primary key (id)
);

create table if not exists job_resume_version (
    id binary(16) not null,
    user_id varchar(255) not null,
    version_name varchar(255) not null,
    target_role varchar(255) not null,
    resume_text text not null,
    notes text,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    primary key (id)
);

alter table job_generated_task
    add column status varchar(255) not null default 'SUCCESS';

alter table job_generated_task
    add column error_message text;
