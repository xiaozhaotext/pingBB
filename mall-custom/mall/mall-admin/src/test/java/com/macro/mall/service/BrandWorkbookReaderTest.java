package com.macro.mall.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import java.nio.file.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class BrandWorkbookReaderTest {
 @TempDir Path images;
 @Test void actualBrandWorkbookExtractsAllSheetsImagesRetailAndStock()throws Exception {
  String file=System.getProperty("brand.workbook");Assumptions.assumeTrue(file!=null);
  try(var wb=WorkbookFactory.create(Path.of(file).toFile())){
   var rows=BrandWorkbookReader.read(wb,Path.of(file),images);
   assertEquals(567,rows.size());
   for(var row:rows.subList(1,rows.size())) {
    assertEquals("100",row.get(5));assertTrue(new BigDecimal(row.get(4)).signum()>0);
    assertTrue(Files.isRegularFile(images.resolve(Path.of(row.get(6)).getFileName())));
    assertTrue(row.get(1).length()<=140);assertTrue(row.get(2).length()<=255);assertTrue(row.get(8).length()<=10000);
   }
   assertTrue(rows.stream().map(r->r.get(0)).distinct().count()>5);
  }
 }
}
