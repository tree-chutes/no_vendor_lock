package co5.demo;

import co5.backflow.client.StageDescriptor;
import co5.backflow.client.Builder;
import co5.backflow.client.LogData;
import co5.backflow.client.Logging;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

public class DemoBuilder implements Builder<DemoWorkUnit, DemoStages> {

    Logging _Log = new Logging();

    public ArrayList<StageDescriptor<DemoWorkUnit, DemoStages>> build() {
        ArrayList<StageDescriptor<DemoWorkUnit, DemoStages>> s = new ArrayList<>();
        s.add(new StageDescriptor<>((byte) 2, (byte) 2, this::loadExcel, (short) 20, (short) 10, DemoStages.LOAD_EXCEL, true));
        s.add(new StageDescriptor<>((byte) 2, (byte) 2, this::loadDocx, (short) 20, (short) 10, DemoStages.LOAD_DOCX, true));
        s.add(new StageDescriptor<>((byte) 2, (byte) 3, this::renameSheets, (short) 20, (short) 10, DemoStages.RENAME_SHEETS, true));
        s.add(new StageDescriptor<>((byte) 2, (byte) 3, this::addText, (short) 20, (short) 10, DemoStages.ADD_TEXT, true));
        s.add(new StageDescriptor<>((byte) 3, (byte) 2, this::setPayload, (short) 20, (short) 10, DemoStages.SET_PAYLOAD, true));
        return s;
    }

    public BiFunction<UUID, Map<String,String>, DemoWorkUnit> getWorkUnitFactory() {
        return this::getWorkUnit;
    }

    private DemoWorkUnit getWorkUnit(UUID id, Map<String,String> rp) {
        return new DemoWorkUnit(false, id, rp);
    }

    public Function<String, Boolean> getAuthorizer() {
        return this::auth;
    }

    private Boolean auth(String header) {
        return true;
    }

    private DemoWorkUnit loadExcel(DemoWorkUnit tw) {
        DemoWorkUnit ret = tw;
        try {
            tw._Workbook = new XSSFWorkbook(new ByteArrayInputStream(tw.payload));
        } catch (Exception ex) {
            _Log.print(new LogData(DemoStages.LOAD_EXCEL.name(), tw.id, ex.getMessage()));
            ret = null;            
        } finally {
            tw.payload = null;
        }
        return ret;
    }

    private DemoWorkUnit loadDocx(DemoWorkUnit tw) {
        DemoWorkUnit ret = tw;
        try {
            tw._Docx = new XWPFDocument(new ByteArrayInputStream(tw.payload));
        } catch (Exception ex) {
            _Log.print(new LogData(DemoStages.LOAD_DOCX.name(), tw.id, ex.getMessage()));
            ret = null;            
        } finally {
            tw.payload = null;
        }
        return ret;
    }

    private DemoWorkUnit renameSheets(DemoWorkUnit tw) {
        tw._Workbook.setSheetName(0, tw._PrintMe);
        return tw;
    }

    private DemoWorkUnit addText(DemoWorkUnit tw) {
        XWPFParagraph p1 = tw._Docx.createParagraph();
        XWPFRun r1 = p1.createRun();
        r1.setBold(true);
        r1.setText(tw._PrintMe);
        r1.setTextPosition(100);        
        return tw;
    }

    private DemoWorkUnit setPayload(DemoWorkUnit tw) {
        DemoWorkUnit ret = tw;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (tw._Workbook != null)
                tw._Workbook.write(baos);
            else
                tw._Docx.write(baos);
            tw.payload = baos.toByteArray();
            ret.httpStatus = 200;
            ret.done = true;
        } catch (IOException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
            ret = null;
        }
        return ret;
    }
}
