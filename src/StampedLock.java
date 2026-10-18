
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


public class StampedLock {
 
    public static void main(String[] args) throws Exception {
    	// Número de threads de writers e readers
        final int writers = 100;
        final int readers = 100;
        final int movesPerWriter = 200000;
 
        Point point = new Point();
        
        // Criação de threads
        ExecutorService pool = Executors.newFixedThreadPool(writers + readers);
        
        // Execução de threads todas ao mesmo tempo
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch writersDone = new CountDownLatch(writers);
 
        // Contagem de leituras usando AtomicLong para não ter problemas de concorrência
        AtomicLong totalReads = new AtomicLong();
        AtomicLong inconsistentReads = new AtomicLong();
 
        // Roda todos as threads de escrita
        for (int i = 0; i < writers; i++) {
        	// Insere uma função em cada thread
            pool.submit(() -> {
                try {
                	// Espera todas as outras threads estarem prontas para executar
                    startGate.await();
                    // Faz a escrita várias vezes
                    for (int j = 0; j < movesPerWriter; j++) {
                        point.move(1.0, 1.0);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                	// Conta quando a thread acabar
                    writersDone.countDown();
                }
            });
        }
 
        // Roda todas as threads de leitura
        for (int i = 0; i < readers; i++) {
        	// Insere uma função em cada thread
            pool.submit(() -> {
                try {
                	// Espera todas as outras threads estarem prontas para executar
                    startGate.await();
                    // Contagem de leituras boas e ruins
                    long reads = 0, bad = 0;
                    
                    // Leitores tentam ler a todo momento para testar concorrência
                    while (writersDone.getCount() > 0) {
                    	// Função de leitura
                        double[] s = point.snapshot();
                        // Contagem de leitura
                        reads++;
                        // Detecção de leitura ruim
                        if (s[0] != s[1]) bad++;
                    }
                    // Contagem de leituras adicionado no AtomicLong
                    totalReads.addAndGet(reads);
                    inconsistentReads.addAndGet(bad);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
 
        // Coleta do tempo inicial
        long t0 = System.nanoTime();
        // Inicia todas as threads ao mesmo tempo
        startGate.countDown();
        // Espera todos as threads de escrita terminarem
        writersDone.await();
        // Fecha as threads
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        // Coleta do tempo final
        long elapsedMs = (System.nanoTime() - t0) / 1000000;
 
        // Detecção de erro entre o esperado e o real
        double[] finalPos = point.snapshot();
        double expected = (double) writers * movesPerWriter;
 
        // Amostra de resultados
        System.out.println("=== Resultado ==="
        					+"\nEscritores: "+ writers +" | Leitores: "+ readers +" | Tempo: "+ elapsedMs +" ms"
        					+"\nPosição final: ("+ finalPos[0] +","+ finalPos[1] +") | Esperado: ("+ expected +","+ expected +")"
        					+"\nTotal de leituras: "+ totalReads.get()
        					+"\nLeituras otimistas que caíram no fallback: "+ point.fallbacks.get()
        					+"\nLeituras inconsistentes (x != y): "+ inconsistentReads.get()
        );
 
        if (finalPos[0] == expected && finalPos[1] == expected && inconsistentReads.get() == 0) {
        	System.out.println("OK: nenhuma escrita perdida e nenhuma leitura inconsistente.");
        } else {
        	System.out.println("FALHA: houve inconsistência!");
        }
    }
}