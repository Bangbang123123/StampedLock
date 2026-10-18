
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;
 
class Point {
    private double x, y;
    public final StampedLock sl = new StampedLock();
    public final AtomicLong fallbacks = new AtomicLong();

    // Função para escrever
    public void move(double dx, double dy) {
    	// Bloqueio de outras escritas
        long stamp = sl.writeLock();
        try {
        	// Escrita
            x += dx;
            y += dy;
        } finally {
        	// Liberação da escrita
            sl.unlockWrite(stamp);
        }
    }
    
    // Função para ler
    public double[] snapshot() {
    	// Testa a leitura
        long stamp = sl.tryOptimisticRead();
        double cx = x, cy = y;
        
        // Testa se a leitura aconteceu no mesmo tempo que uma escrita
        if (!sl.validate(stamp)) {
        	// Bloqueia a leitura de outras threads
            fallbacks.incrementAndGet();
            stamp = sl.readLock();
            try {
            	// Leitura
                cx = x;
                cy = y;
            } finally {
            	// Desbloqueia a leitura
                sl.unlockRead(stamp);
            }
        }
        // Retorna o valor
        return new double[]{cx, cy};
    }
}