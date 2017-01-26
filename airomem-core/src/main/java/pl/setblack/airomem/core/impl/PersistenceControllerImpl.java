/* Copyright (c) Jarek Ratajski, Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0 */
package pl.setblack.airomem.core.impl;

import com.thoughtworks.xstream.XStream;
import org.prevayler.Prevayler;
import pl.setblack.airomem.core.disk.PersistenceDiskHelper;
import pl.setblack.badass.Politician;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;

/**
 * Controller of persistence system.
 * <p>
 * Use this to perform queries and commands on system.
 *
 * @param <ROOT> mutable interface to system
 * @author jarekr
 */
public class PersistenceControllerImpl<ROOT extends Serializable>
        implements PersistenceController<ROOT> {

    private Prevayler<RoyalFoodTester<ROOT>> prevayler;

    private final Path path;

    private final boolean isTransient;

    public PersistenceControllerImpl(final Path path, boolean isTransient) {
        this.path = path;
        this.isTransient = isTransient;
    }

    /**
     * Close system.
     * <p>
     * This couses snapshot of system to be done. After this operation
     * Controller should be no more usable.
     */
    public void close() {
        Politician.beatAroundTheBush(() -> {
            if (this.prevayler != null) {
                this.optionalSnapshot();
                this.prevayler.close();
                this.prevayler = null;
            }
        });
    }

    /**
     * Shut system immediatelly.
     * <p>
     * No snaphsot is done. After load system will be restored using Commands.
     */
    @Override
    public void shut() {
        Politician.beatAroundTheBush(() -> {
            this.prevayler.close();
            this.prevayler = null;
        });
    }

    public void initSystem(final Prevayler<RoyalFoodTester<ROOT>> prevayler) {
        this.prevayler = prevayler;
        this.optionalSnapshot();
    }

    /**
     * Query system (immutable view of it).
     * <p>
     * Few things to remember: 1. if operations done on system (using query) do
     * make some changes they will not be preserved (for long) 2. it is possible
     * to return any object from domain (including IMMUTABLE root) and perform
     * operations later on (but the more You do inside Query the safer).
     *
     * @param <RESULT> result of query
     * @param query    lambda (or query implementation) with operations
     * @return calculated result
     */
    public <RESULT> RESULT query(Query<ROOT, RESULT> query) {
        return query.evaluate(getImmutable());
    }

    /**
     * Perform command on system.
     * <p>
     * Inside command can be any code doing any changes. Such changes are
     * guaranteed to be preserved (if only command ended without exception).
     *
     * @param cmd
     */
    public <R> R executeAndQuery(ContextCommand<ROOT, R> cmd) {
        return Politician.beatAroundTheBush(() -> this.prevayler.execute(new InternalTransaction<>(cmd)));
    }

    /**
     * Perform command on system.
     * <p>
     * Inside command can be any code doing any changes. Such changes are
     * guaranteed to be preserved (if only command ended without exception).
     *
     * @param cmd
     */
    @Override
    public <R> R executeAndQuery(Command<ROOT, R> cmd) {
        return this.executeAndQuery((ContextCommand<ROOT, R>) cmd);
    }

    @Override
    public void execute(VoidCommand<ROOT> cmd) {
        this.executeAndQuery((Command<ROOT, Void>) cmd);
    }

    @Override
    public void execute(VoidContextCommand<ROOT> cmd) {
        this.executeAndQuery((ContextCommand<ROOT, Void>) cmd);
    }

    private ROOT getObject() {
        return this.prevayler.prevalentSystem().getWorkObject();
    }

    private ROOT getImmutable() {
        return this.getObject();
    }

    @Override
    public boolean isOpen() {
        return this.prevayler != null;
    }

    public void deleteFolder() {
        PersistenceDiskHelper.delete(this.path);
    }

    @Override
    public void erase() {
        this.close();
        this.deleteFolder();
    }

    private void optionalSnapshot() {
            if ( !isTransient) {
                snapshot();
            }
    }

    @Override
    public void snapshot() {
        Politician.beatAroundTheBush(() -> this.prevayler.takeSnapshot());
    }

    @Override
    public void snapshotXML(Path xmlFile) throws IOException {
        final ROOT root  =this.prevayler.prevalentSystem().getWorkObject();
        XStream xStream = new XStream();
        final FileOutputStream fout = new FileOutputStream(xmlFile.toFile());
        xStream.toXML(root, fout);
        fout.close();
    }
}
