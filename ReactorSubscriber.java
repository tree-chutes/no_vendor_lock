package co5.backflow.client;

import java.util.UUID;
import java.util.function.UnaryOperator;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import reactor.core.publisher.Sinks;

public class ReactorSubscriber implements Subscriber<FluxMessage>{
    private int requested;
    private Subscription subscription;
    private final int REQUEST_SIZE = 50;
    private final UnaryOperator<FluxMessage> applyNext;
    private final UnaryOperator<Void> applyComplete;
    private final UnaryOperator<Throwable> applyError;
    private final Logging logger;
    private final UUID id;
  
    public ReactorSubscriber(UUID i, Sinks.Many<FluxMessage> s, UnaryOperator<FluxMessage> on, UnaryOperator<Void> oc, Logging l
        , UnaryOperator<Throwable> oe){
        logger = l;
        id = i;
        assert on != null;
        applyNext = on;
        assert oc != null;        
        applyComplete = oc;
        assert oe != null;
        applyError = oe;
    }

    @Override
    public void onSubscribe(Subscription s) {
        this.subscription = s;
        this.requested = REQUEST_SIZE;
        this.subscription.request(requested);
    }
    
    @Override
    public void onNext(FluxMessage m) {
        try {
            this.applyNext.apply(m);
            if (--this.requested == 0){
                this.requested = REQUEST_SIZE;
                this.subscription.request(requested);                
            }
            this.logger.print(new LogData(id.toString(), String.format("%s received successfully", m.id)));
        } catch (Exception e) {
            e.printStackTrace();
            this.logger.print(new LogData(id.toString(), String.format("%s failed to process", m.id)));
        }
    }

    @Override
    public void onError(Throwable t) {
        this.logger.print(new LogData(id.toString(), String.format("Flux threw: %s", t.getMessage())));
        this.applyError.apply(t);
    }

    @Override
    public void onComplete() {
        this.logger.print(new LogData(id.toString(), "Flux completed successfully"));
        this.applyComplete.apply(null);
    }            
}
