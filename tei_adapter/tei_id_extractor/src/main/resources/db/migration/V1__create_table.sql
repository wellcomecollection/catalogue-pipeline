CREATE TABLE ${database}.${tableName} (
                 path varchar(255) NOT NULL,
                 id varchar(255) NOT NULL,
                 timeModified DATETIME NOT NULL,
                 PRIMARY KEY (path),
                 CONSTRAINT UNIQUE (id));
