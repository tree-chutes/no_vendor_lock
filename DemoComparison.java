package co5.demo;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

import co5.backflow.client.Builder;
import co5.backflow.client.StageDescriptor;

public class DemoComparison implements Builder<DemoWorkUnit, DemoStages> {

    @Override
    public ArrayList<StageDescriptor<DemoWorkUnit, DemoStages>> build() {
        ArrayList<StageDescriptor<DemoWorkUnit, DemoStages>> s = new ArrayList<>();
        s.add(new StageDescriptor<>((byte) 2, (byte) 2, this::demoStage1, (short) 20, (short) 10, DemoStages.COMPARISON_STAGE1, true, false));
        s.add(new StageDescriptor<>((byte) 2, (byte) 2, this::demoStage2, (short) 20, (short) 10, DemoStages.COMPARISON_STAGE2, true, false));
        return s;
    }

    @Override
    public Function<String, Boolean> getAuthorizer() {
        return this::auth;        
    }

    @Override
    public BiFunction<UUID, Map<String, String>, DemoWorkUnit> getWorkUnitFactory() {
        return this::getWorkUnit;
    }

    private DemoWorkUnit getWorkUnit(UUID id, Map<String,String> rp) {
        return new DemoWorkUnit(false, id, rp);
    }

    private Boolean auth(String header) {
        return true;
    }

    private DemoWorkUnit demoStage1(DemoWorkUnit wu){
        wu.payload = "From Java 1".getBytes();
        return wu;
    }

    private DemoWorkUnit demoStage2(DemoWorkUnit wu){
        wu.payload = String.format("%s/%s", new String(wu.payload), "From Java2").getBytes();
        wu.done = true;
        wu.httpStatus = 200;
        return wu;
    }
}
