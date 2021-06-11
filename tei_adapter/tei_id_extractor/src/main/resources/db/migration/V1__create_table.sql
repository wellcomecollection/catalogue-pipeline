CREATE TABLE ${database}.${tableName} (
                 path varchar(255) NOT NULL,
                 id varchar(255) NOT NULL,
                 timeModified BIGINT UNSIGNED NOT NULL,
                 PRIMARY KEY (path),
                 CONSTRAINT UNIQUE (id));
