package br.ufrn.dpb.ledger.queue;

import br.ufrn.dpb.ledger.channel.SingleSocketChannel;
import br.ufrn.dpb.ledger.controller.LedgerController;
import br.ufrn.dpb.ledger.protocol.Message;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class SingularUpdateQueue implements Closeable {
    private static final Logger LOGGER = Logger.getLogger(SingularUpdateQueue.class.getName());

    private record UpdateTask(Message request, SingleSocketChannel channel) {}

    private final BlockingQueue<UpdateTask> queue;
    private final LedgerController controller;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread workerThread;

    public SingularUpdateQueue(LedgerController controller, int capacity) {
        this.controller = controller;
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    public SingularUpdateQueue(LedgerController controller) {
        this(controller, 10000);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            workerThread = new Thread(this::processQueue, "SingularUpdateQueueWorker");
            workerThread.setDaemon(true);
            workerThread.start();
        }
    }

    public boolean submit(Message request, SingleSocketChannel channel) {
        return queue.offer(new UpdateTask(request, channel));
    }

    private void processQueue() {
        while (running.get()) {
            try {
                UpdateTask task = queue.take();
                Message request = task.request();
                SingleSocketChannel channel = task.channel();

                Message response;
                try {
                    byte[] responsePayload = controller.handleOperation(request.getPayload());
                    response = new Message(Message.Type.RESPONSE, request.getRequestId(), responsePayload);
                } catch (Exception e) {
                    byte[] errorPayload = (e.getMessage() != null ? e.getMessage() : "Ledger error")
                            .getBytes(StandardCharsets.UTF_8);
                    response = new Message(Message.Type.ERROR, request.getRequestId(), errorPayload);
                }

                if (channel.isConnected()) {
                    channel.writeMessage(response);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.severe(() -> "Error in SingularUpdateQueue worker: " + e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        running.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }
}
