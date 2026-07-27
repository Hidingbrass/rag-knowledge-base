create table app_user (
    id binary(16) not null,
    username varchar(100) not null,
    password_hash varchar(255) not null,
    display_name varchar(100) not null,
    department varchar(100) not null,
    role varchar(50) not null,
    created_at timestamp(6) not null,
    primary key (id)
);

create unique index uk_app_user_username
    on app_user (username);
