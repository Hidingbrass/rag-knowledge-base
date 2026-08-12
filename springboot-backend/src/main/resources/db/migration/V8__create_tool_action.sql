create table tool_action (
    id binary(16) not null,
    user_id varchar(255) not null,
    department varchar(255) not null,
    session_id binary(16) not null,
    assistant_message_id binary(16) not null,
    tool_name varchar(100) not null,
    arguments_json text not null,
    status varchar(50) not null,
    expires_at timestamp(6) not null,
    created_at timestamp(6) not null,
    executed_at timestamp(6),
    result_summary text,
    primary key (id)
);

create index idx_tool_action_user_created
    on tool_action (user_id, created_at);

create index idx_tool_action_session_created
    on tool_action (session_id, created_at);
