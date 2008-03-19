package liquibase.database.structure;

import liquibase.database.Database;
import liquibase.database.HibernateDatabase;
import liquibase.diff.DiffStatusListener;
import liquibase.exception.JDBCException;
import liquibase.log.LogFactory;
import liquibase.util.StringUtils;
import org.hibernate.cfg.Configuration;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.MySQL5InnoDBDialect;
import org.hibernate.engine.Mapping;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

public class HibernateDatabaseSnapshot  implements DatabaseSnapshot {
    private HibernateDatabase database;

    public static void main(String[] args) throws Exception {
//        URLClassLoader classLoader = new URLClassLoader(new URL[] {
//                new URL("file:///othersvn/liquibase/core/src/samples/hibernate")
//        }, Thread.currentThread().getContextClassLoader());
//
//        HibernateDatabaseSnapshot snapshot = (HibernateDatabaseSnapshot) Class.forName(HibernateDatabaseSnapshot.class.getName(), true, classLoader).getConstructor(String.class, Database.class).newInstance("C:/othersvn/liquibase/core/src/samples/hibernate/Hibernate.cfg.xml", null);

        System.out.println(new File("Hibernate.cfg.xml").toURL());
        HibernateDatabaseSnapshot snapshot = new HibernateDatabaseSnapshot(new HibernateDatabase("Hibernate.cfg.xml"));
    }

    private Set<liquibase.database.structure.Table> tables = new HashSet<liquibase.database.structure.Table>();
    private Set<Column> columns = new HashSet<Column>();
    private Set<ForeignKey> foreignKeys = new HashSet<ForeignKey>();
    private Set<Index> indexes = new HashSet<Index>();
    private Set<PrimaryKey> primaryKeys = new HashSet<PrimaryKey>();
    private Set<Sequence> sequences = new HashSet<Sequence>();


    private Map<String, liquibase.database.structure.Table> tablesMap = new HashMap<String, liquibase.database.structure.Table>();
    private Map<String, Column> columnsMap = new HashMap<String, Column>();

    private Set<DiffStatusListener> statusListeners;

    private static final Logger log = LogFactory.getLogger();

    public HibernateDatabaseSnapshot(HibernateDatabase database) throws JDBCException {
        try {
            Configuration cfg = new Configuration();
            cfg.configure(database.getConfigFile());
            this.database = database;

            Dialect dialect = (Dialect) Class.forName(cfg.getProperty("dialect")).newInstance();
            Mapping mapping = cfg.buildMapping();

//            Dialect dialect = new MySQL5InnoDBDialect();

            Iterator tableMappings = cfg.getTableMappings();
            while (tableMappings.hasNext()) {
                org.hibernate.mapping.Table hibernateTable = (org.hibernate.mapping.Table) tableMappings.next();
                if (hibernateTable.isPhysicalTable()) {
                    Table table = new Table(hibernateTable.getName());
                    System.out.println("seen table " + table.getName());

                    tablesMap.put(table.getName(), table);

                    Iterator columnIterator = hibernateTable.getColumnIterator();
                    while (columnIterator.hasNext()) {
                        org.hibernate.mapping.Column hibernateColumn = (org.hibernate.mapping.Column) columnIterator.next();
                        Column column = new Column();
                        column.setName(hibernateColumn.getName());
//todo:                        column.setAutoIncrement(hibernateColumn.);
                        column.setColumnSize(hibernateColumn.getLength());
                        column.setDataType(hibernateColumn.getSqlTypeCode(mapping));
                        column.setDecimalDigits(hibernateColumn.getPrecision());
                        column.setDefaultValue(hibernateColumn.getDefaultValue());
                        column.setNullable(hibernateColumn.isNullable());
                        column.setPrimaryKey(isPrimaryKey(hibernateTable, hibernateColumn));
                        column.setTable(table);
                        column.setTypeName(hibernateColumn.getSqlType(dialect, mapping));
                        column.setUnique(hibernateColumn.isUnique());

                        columnsMap.put(table.getName() + "." + column.getName(), column);
                        table.getColumns().add(column);
                    }

                    Iterator indexIterator = hibernateTable.getIndexIterator();
                    while (indexIterator.hasNext()) {
                        org.hibernate.mapping.Index hibernateIndex = (org.hibernate.mapping.Index) indexIterator.next();
                        Index index = new Index();
                        index.setTable(table);
                        index.setName(hibernateIndex.getName());
//todo?                        index.setFilterCondition(hibernateIndex.getFilterCondition());
                        columnIterator = hibernateIndex.getColumnIterator();
                        while (columnIterator.hasNext()) {
                            org.hibernate.mapping.Column hibernateColumn = (org.hibernate.mapping.Column) columnIterator.next();
                            index.getColumns().add(hibernateColumn.getName());
                        }

                        indexes.add(index);
                    }

                }
            }

            tableMappings = cfg.getTableMappings();
            while (tableMappings.hasNext()) {
                org.hibernate.mapping.Table hibernateTable = (org.hibernate.mapping.Table) tableMappings.next();
                if (hibernateTable.isPhysicalTable()) {

                    Iterator fkIterator = hibernateTable.getForeignKeyIterator();
                    while (fkIterator.hasNext()) {
                        org.hibernate.mapping.ForeignKey hibernateForeignKey = (org.hibernate.mapping.ForeignKey) fkIterator.next();
                        if (hibernateForeignKey.getTable() != null
                                && hibernateForeignKey.getReferencedTable() != null
                                && hibernateForeignKey.isPhysicalConstraint()) {
                            ForeignKey fk = new ForeignKey();
                            fk.setName(hibernateForeignKey.getName());
                            fk.setForeignKeyTable(tablesMap.get(hibernateForeignKey.getTable().getName()));
                            fk.setForeignKeyColumns(StringUtils.join((Collection<String>) hibernateForeignKey.getColumns(), ", "));
                            fk.setForeignKeyTable(tablesMap.get(hibernateForeignKey.getReferencedTable().getName()));
                            fk.setForeignKeyColumns(StringUtils.join((Collection<String>) hibernateForeignKey.getReferencedColumns(), ", "));
//todo                            fk.setDeferrable(hibernateForeignKey.);
//todo                            fk.setInitiallyDeferred();
                            foreignKeys.add(fk);
                        }

                    }

//                    script.add(
//                            table.sqlCreateString(
//                                    dialect,
//                                    mapping,
//                                    defaultCatalog,
//                                    defaultSchema
//                                )
//                        );
//                    Iterator comments = table.sqlCommentStrings( dialect, defaultCatalog, defaultSchema );
//                    while ( comments.hasNext() ) {
//                        script.add( comments.next() );
//                    }
                }

            }

            this.tables = new HashSet<Table>(tablesMap.values());
            this.columns = new HashSet<Column>(columnsMap.values());

        } catch (Exception e) {
            throw new JDBCException(e);
        }
    }

    private boolean isPrimaryKey(org.hibernate.mapping.Table hibernateTable, org.hibernate.mapping.Column hibernateColumn) {
        org.hibernate.mapping.PrimaryKey key = hibernateTable.getPrimaryKey();
        if (key == null) {
            return false;
        }
        Iterator columnIterator = key.getColumnIterator();
        while (columnIterator.hasNext()) {
            if (columnIterator.next().equals(hibernateColumn)) {
                return true;
            }
        }
        return false;
    }

    public Database getDatabase() {
        return database;
    }

    public Set<Table> getTables() {
        return tables;
    }

    public Set<View> getViews() {
        return new HashSet<View>();
    }

    public Column getColumn(Column column) {
        if (column.getTable() == null) {
            return columnsMap.get(column.getView().getName() + "." + column.getName());
        } else {
            return columnsMap.get(column.getTable().getName() + "." + column.getName());
        }
    }

    public Column getColumn(String tableName, String columnName) {
        return columnsMap.get(tableName + "." + columnName);
    }

    public Set<Column> getColumns() {
        return columns;
    }

    public Set<ForeignKey> getForeignKeys() {
        return foreignKeys;
    }

    public Set<Index> getIndexes() {
        return indexes;
    }

    public Set<PrimaryKey> getPrimaryKeys() {
        return primaryKeys;
    }


    public Set<Sequence> getSequences() {
        return sequences;
    }

      public Table getTable(String tableName) {
        for (Table table : getTables()) {
            if (table.getName().equalsIgnoreCase(tableName)) {
                return table;
            }
        }
        return null;
    }

    public ForeignKey getForeignKey(String foreignKeyName) {
        for (ForeignKey fk : getForeignKeys()) {
            if (fk.getName().equalsIgnoreCase(foreignKeyName)) {
                return fk;
            }
        }
        return null;
    }

    public Sequence getSequence(String sequenceName) {
        for (Sequence sequence : getSequences()) {
            if (sequence.getName().equalsIgnoreCase(sequenceName)) {
                return sequence;
            }
        }
        return null;
    }

    public Index getIndex(String indexName) {
        for (Index index : getIndexes()) {
            if (index.getName().equalsIgnoreCase(indexName)) {
                return index;
            }
        }
        return null;
    }

    public View getView(String viewName) {
        for (View view : getViews()) {
            if (view.getName().equalsIgnoreCase(viewName)) {
                return view;
            }
        }
        return null;
    }

    public PrimaryKey getPrimaryKey(String pkName) {
        for (PrimaryKey pk : getPrimaryKeys()) {
            if (pk.getName().equalsIgnoreCase(pkName)) {
                return pk;
            }
        }
        return null;
    }
}