create table knowledge_base (
    id binary(16) not null,
    name varchar(255) not null,
    description text,
    owner_id varchar(255) not null,
    department varchar(255) not null,
    created_at timestamp(6) not null,
    primary key (id)
);

create table knowledge_document (
    id binary(16) not null,
    knowledge_base_id binary(16) not null,
    fast_api_document_id varchar(255),
    filename varchar(255) not null,
    file_hash varchar(255),
    status varchar(255) not null,
    chunk_count integer not null,
    error_message text,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    primary key (id)
);

create index idx_document_kb_hash
    on knowledge_document (knowledge_base_id, file_hash);

create index idx_document_kb_created_at
    on knowledge_document (knowledge_base_id, created_at);

create table chat_session (
    id binary(16) not null,
    knowledge_base_id binary(16) not null,
    user_id varchar(255) not null,
    title varchar(255) not null,
    created_at timestamp(6) not null,
    primary key (id)
);

create index idx_chat_session_user_created_at
    on chat_session (user_id, created_at);

create table chat_message (
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

create index idx_chat_message_session_created_at
    on chat_message (session_id, created_at);

create table job_analysis_task (
    id binary(16) not null,
    user_id varchar(255) not null,
    resume_text text not null,
    job_description text not null,
    match_score integer not null,
    result_json text not null,
    created_at timestamp(6) not null,
    primary key (id)
);

create index idx_job_task_user_created_at
    on job_analysis_task (user_id, created_at);

create table job_generated_task (
    id binary(16) not null,
    user_id varchar(255) not null,
    task_type varchar(255) not null,
    resume_text text not null,
    job_description text not null,
    result_json text not null,
    created_at timestamp(6) not null,
    primary key (id)
);

create index idx_job_generated_task_user_type_created_at
    on job_generated_task (user_id, task_type, created_at);

create table job_favorite (
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

create index idx_job_favorite_user_created_at
    on job_favorite (user_id, created_at);

create table job_resume_version (
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

create index idx_job_resume_version_user_updated_at
    on job_resume_version (user_id, updated_at);
