/*
 * Copyright 2004-2011 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.command.ddl;

import org.h2.command.CommandInterface;
import org.h2.constant.ErrorCode;
import org.h2.engine.Database;
import org.h2.engine.Right;
import org.h2.engine.Session;
import org.h2.message.DbException;
import org.h2.schema.Schema;
import org.h2.table.Table;

/**
 * This class represents the statement
 * ALTER TABLE RENAME
 */
public class AlterTableRename extends SchemaCommand {

    private Table oldTable;
    private String newTableName;

    public AlterTableRename(Session session, Schema schema) {
        super(session, schema);
    }

    public void setOldTable(Table table) {
        oldTable = table;
    }

    public void setNewTableName(String name) {
        newTableName = name;
    }

    public int update() {
        session.commit(true);
        Database db = session.getDatabase();
        if (getSchema().findTableOrView(session, newTableName) != null || newTableName.equals(oldTable.getName())) {
            throw DbException.get(ErrorCode.TABLE_OR_VIEW_ALREADY_EXISTS_1, newTableName);
        }
        session.getUser().checkRight(oldTable, Right.ALL);
        if (oldTable.isTemporary()) {
            throw DbException.getUnsupportedException("TEMP TABLE");
        }
        db.renameSchemaObject(session, oldTable, newTableName);
        return 0;
    }

    public int getType() {
        return CommandInterface.ALTER_TABLE_RENAME;
    }

}
