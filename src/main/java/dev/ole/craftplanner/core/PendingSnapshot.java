package dev.ole.craftplanner.core;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
public final class PendingSnapshot<T> {
    private CompletableFuture<T> future;
    public boolean pending() { return future!=null; }
    public void request(CompletableFuture<T> next) { if(future!=null)throw new IllegalStateException("Already pending"); future=next; }
    public Optional<T> poll() { if(future==null || !future.isDone())return Optional.empty(); var ready=future; future=null; return Optional.ofNullable(ready.join()); }
    public void clear() { if(future!=null)future.cancel(false); future=null; }
}
