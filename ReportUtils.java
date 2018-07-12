package vn.com.fwd.service.exporter;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import vn.com.fwd.constants.MisReportConstants;
import vn.com.fwd.model.TreeNode;
import vn.com.fwd.service.OdsQueryService;

@Slf4j
@Service
public class ReportUtils {
	@Value("${report.temp}")
    private String folderTemp;
	@Value("${number-pattern.submission}")
	private String numberPatternSubmission;
	@Autowired
	private OdsQueryService odsQueryService;
	
	/**
	 * method write data to excel
	 * @param columnsReport
	 * @param lstData
	 * @param filePathExcel
	 * @param strNumberPattern
	 * @param numberFormat
	 * @return : file path excel
	 * @throws Exception
	 */
	public String writeFileExcelReport(ColumnReport[] columnsReport, List<Map<String, Object>> lstData, String filePathExcel, String strNumberPattern, String numberFormat) throws Exception {
    	DateFormat df = new SimpleDateFormat("dd-MMM-yyyy");
        try {
            // excel
            XSSFWorkbook workbook = new XSSFWorkbook();
            XSSFSheet sheet = workbook.createSheet("#Data");
            int rowNum = 0;
            int columnNumber = columnsReport.length;
            DataFormat dataformat = workbook.createDataFormat();
            CellStyle styleNumber = workbook.createCellStyle();
            styleNumber.setDataFormat(dataformat.getFormat(numberFormat));
            CellStyle styleDate = workbook.createCellStyle();
            styleDate.setDataFormat(dataformat.getFormat("dd-MMM-yyyy"));
            // write header
            Row rowHeader = sheet.createRow(rowNum++);
            for (int i = 0; i < columnNumber; i++) {
                Cell cellHeader = rowHeader.createCell(i);
                cellHeader.setCellValue(columnsReport[i].getHeaderText());
            }

            // write data
            if (lstData != null && lstData.size() > 0) {
                for (Map<String, Object> map : lstData) {
                    Row row = sheet.createRow(rowNum++);
                    for (int i = 0; i < columnNumber; i++) {
                    	Cell cellData = row.createCell(i);
                    	ColumnReport columnReport = columnsReport[i];
                    	// get value cell original
                    	String valueCell = String.valueOf(map.get(columnReport.getField()));
                    	// regex-matches value cell
                    	String patternDataColumn = columnReport.getPattern();
                    	if (patternDataColumn != null && !patternDataColumn.isEmpty()) {
                    		valueCell = regexMatches(valueCell, patternDataColumn);
                    	}
                    	// type of column
                    	String typeDataColumn = columnReport.getType();
                    	if (typeDataColumn == null || typeDataColumn.isEmpty()) {
	                    	// check is date
	                    	try {
								Date dateTmp = df.parse(valueCell);
								cellData.setCellValue(dateTmp);
								cellData.setCellStyle(styleDate);
							} catch (ParseException e) {
								// check number
								try {
									String strRegex = regexMatches(valueCell, strNumberPattern);
									if (strRegex.length() == valueCell.length()) {
										long intTmp = Long.parseLong(strRegex.replaceAll(",", ""));
										cellData.setCellValue(intTmp);
										cellData.setCellStyle(styleNumber);
									} else if ("-".equals(valueCell)) {
										cellData.setCellValue(0);
										cellData.setCellStyle(styleNumber);
									} else {
										cellData.setCellValue(valueCell);
									}
								} catch (Exception e2) {
									cellData.setCellValue(valueCell);
								}
							}
						} else if (typeDataColumn == MisReportConstants.DataReportConstant.STRING) {
							cellData.setCellValue(valueCell);
						} else if (typeDataColumn == MisReportConstants.DataReportConstant.INT) {
							if (valueCell.isEmpty()) {
								valueCell = "0";
							}
							cellData.setCellValue(Long.parseLong(valueCell));
							cellData.setCellStyle(styleNumber);
						} else if (typeDataColumn == MisReportConstants.DataReportConstant.DOUBLE) {
							if (valueCell.isEmpty()) {
								valueCell = "0";
							}
							cellData.setCellValue(Double.parseDouble(valueCell));
							cellData.setCellStyle(styleNumber);
						} else if (typeDataColumn == MisReportConstants.DataReportConstant.DATE) {
							cellData.setCellValue(df.parse(valueCell));
							cellData.setCellStyle(styleDate);
						}
                    }
                }
            }

            FileOutputStream outputStream = new FileOutputStream(filePathExcel);
            workbook.write(outputStream);
            workbook.close();
            return filePathExcel;
        } catch (Exception e) {
            throw e;
        }
    }
    
	/**
	 * method regex matches
	 * @param input
	 * @param pattern
	 * @return
	 */
    private String regexMatches(String input, String pattern) {
		try {
			Pattern r = Pattern.compile(pattern);
			Matcher m = r.matcher(input);
			if (m.find()) {
				return m.group();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}
    
    /**
     * method export Submission
     * @param treeNodes
     * @param dateReport
     * @param reportType
     * @return
     * @throws Exception
     */
    public String exportSubmission(List<TreeNode<Map<String, Object>>> treeNodes, String dateReport, String reportType) throws Exception {
    	try {
    		List<Map<String, Object>> lstData = new ArrayList<>();
    		// add tree to list
    		addTreeToList(treeNodes, lstData);
    		for (Map<String, Object> map : lstData) {
    			float mtdApe = ((BigDecimal) map.get("mtdApe")).floatValue();
    			int mtdCases = (Integer)map.get("mtdCases");
    			if (mtdCases == 0) {
    				map.put("mtdCaseSize", 0);
    			} else {
    				map.put("mtdCaseSize", (double)Math.round(10 * mtdApe/mtdCases)/10);
    			}
    			float prevMtdSameDayMtdApe = ((BigDecimal) map.get("prevMtdSameDayMtdApe")).floatValue();
    			if (prevMtdSameDayMtdApe == 0) {
    				map.put("percentOfPrevMthSameDayMtdApe", 0);
    			} else {
    				map.put("percentOfPrevMthSameDayMtdApe", (double)Math.round(1000 * mtdApe/prevMtdSameDayMtdApe)/10);
    			}
    			float prevMthTotalApe = ((BigDecimal) map.get("prevMthTotalApe")).floatValue();
    			if (prevMthTotalApe == 0) {
    				map.put("percentOfPrevMthTotalApe", 0);
    			} else {
    				map.put("percentOfPrevMthTotalApe", (double)Math.round(1000 * mtdApe/prevMthTotalApe)/10);
    			}
    			int mtdInforceAgents = (Integer) map.get("mtdInforceAgents");
    			if (mtdInforceAgents == 0) {
    				map.put("mtdPercentActive", 0);
    			} else {
    				map.put("mtdPercentActive", (double)Math.round(1000 * (Integer) map.get("mtdActiveAgent")/mtdInforceAgents)/10);
    			}
    		}
    		// get list column
    		int searchDay = Integer.parseInt(dateReport.split("-")[0]);
    		ColumnReport[] columnsReport;
    		if ("SUBMISSION_QUERY".equals(reportType)) {
    			columnsReport = ReportProperties.getColumnsSubmission(searchDay);
    		} else {
    			columnsReport = ReportProperties.getColumnsSubmissionOnline(searchDay);
    		}
    		// create file Path Excel temporary
            String filePath = getFilePathTempNotExist(reportType, dateReport);
    		return writeFileExcelReport(columnsReport, lstData, filePath, numberPatternSubmission, "###,##0.0");
		} catch (Exception e) {
			log.error(e.getMessage());
			throw e;
		}
    }
    
    /**
     * method export Issuance
     * @param treeNodes
     * @param dateReport
     * @param reportType
     * @return
     * @throws Exception
     */
    public String exportIssuance(List<TreeNode<Map<String, Object>>> treeNodes, String dateReport, String reportType) throws Exception {
    	try {
    		List<Map<String, Object>> lstData = new ArrayList<>();
    		// add tree to list
    		addTreeToList(treeNodes, lstData);
    		for (Map<String, Object> map : lstData) {
    			float mtdApePlan = ((BigDecimal) map.get("mtdApePlan")).floatValue();
    			float mtdApeActual = ((BigDecimal) map.get("mtdApeActual")).floatValue();
    			if (mtdApePlan == 0) {
    				map.put("mtdPerArchived", 0);
    			} else {
    				map.put("mtdPerArchived", (double)Math.round(1000 * mtdApeActual/mtdApePlan)/10);
    			}
    			int mtdCases = (Integer) map.get("mtdCases");
    			if (mtdCases == 0) {
    				map.put("mtdCaseSize", 0);
    			} else {
    				map.put("mtdCaseSize", (double)Math.round(10 * mtdApeActual/mtdCases)/10);
    			}
    			int mtdActiveAgents = (Integer) map.get("mtdActiveAgents");
    			int mtdInforceAgents = (Integer) map.get("mtdInforceAgents");
    			if (mtdInforceAgents == 0) {
    				map.put("mtdPerActive", 0);
    			} else {
    				map.put("mtdPerActive", (double)Math.round(1000 * mtdActiveAgents/mtdInforceAgents)/10);
    			}
    			float mtdApeLastYearSameMtd = ((BigDecimal) map.get("mtdApeLastYearSameMtd")).floatValue();
    			if (mtdApeLastYearSameMtd == 0) {
    				map.put("momGrowth", 0);
    			} else {
    				map.put("momGrowth", (double)Math.round(1000 * mtdApeActual/mtdApeLastYearSameMtd)/10);
    			}
    			float ytdApeActual = ((BigDecimal) map.get("ytdApeActual")).floatValue();
    			float ytdApePlan = ((BigDecimal) map.get("ytdApePlan")).floatValue();
    			if (ytdApePlan == 0) {
    				map.put("ytdPerArchived", 0);
    			} else {
    				map.put("ytdPerArchived", (double)Math.round(1000 * ytdApeActual/ytdApePlan)/10);
    			}
    			int ytdCases = (Integer) map.get("ytdCases");
    			if (ytdCases == 0) {
    				map.put("ytdCaseSize", 0);
    			} else {
    				map.put("ytdCaseSize", (double)Math.round(10 * ytdApeActual/ytdCases)/10);
    			}
    			float ytdApeLastYear = ((BigDecimal) map.get("ytdApeLastYear")).floatValue();
    			if (ytdApeLastYear == 0) {
    				map.put("yoyGrowth", 0);
    			} else {
    				map.put("yoyGrowth", (double)Math.round(1000 * ytdApeActual/ytdApeLastYear)/10);
    			}
    		}
    		// get list column
    		ColumnReport[] columnsReport = ReportProperties.getColumnsIssuance();
    		// create file Path Excel temporary
            String filePath = getFilePathTempNotExist(reportType, dateReport);
    		return writeFileExcelReport(columnsReport, lstData, filePath, numberPatternSubmission, "###,##0.0");
		} catch (Exception e) {
			log.error(e.getMessage());
			throw e;
		}
    }
    
    /**
     * method export Manpower Report
     * @param treeNodes
     * @param dateReport
     * @param reportType
     * @return
     * @throws Exception
     */
    public String exportManpowerReport(List<TreeNode<Map<String, Object>>> treeNodes, String dateReport, String reportType) throws Exception {
    	try {
    		List<Map<String, Object>> lstData = new ArrayList<>();
    		// add tree to list
    		addTreeToList(treeNodes, lstData);
    		// get list column
    		ColumnReport[] columnsReport = null;
    		if ("MANPOWER_AGENT_QUERY".equals(reportType)) {
    			columnsReport = ReportProperties.getColumnsAgencyManpower();
    		} else if ("MANPOWER_BANCA_QUERY".equals(reportType)) {
    			columnsReport = ReportProperties.getColumnsBancaManpower();
    		} else if ("RAG_AGENT_QUERY".equals(reportType)) {
    			columnsReport = ReportProperties.getColumnsRAGScorecard();
    		}
    		// create file Path Excel temporary
            String filePath = getFilePathTempNotExist(reportType, dateReport);
    		return writeFileExcelReport(columnsReport, lstData, filePath, numberPatternSubmission, "###,##0.0");
		} catch (Exception e) {
			log.error(e.getMessage());
			throw e;
		}
    }
    
    /**
     * Method add tree to list
     * @param treeNodes
     * @param lstResult
     * @throws Exception
     */
    private void addTreeToList(List<TreeNode<Map<String, Object>>> treeNodes, List<Map<String, Object>> lstResult) throws Exception {
    	try {
    		if (lstResult == null) {
    			lstResult = new ArrayList<>();
    		}
    		if (treeNodes != null && !treeNodes.isEmpty()) {
				for (int i = treeNodes.size() - 1; i >=0; i--) {
					TreeNode<Map<String, Object>> node = treeNodes.get(i);
					if (node != null) {
						lstResult.add(node.getData());
						List<TreeNode<Map<String, Object>>> lstNodeChild = node.getChildren();
						if (lstNodeChild != null && !lstNodeChild.isEmpty()) {
							addTreeToList(lstNodeChild, lstResult);
						}
					}
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage());
			throw e;
		}
    }
    
    /**
     * method export KRA Agent report
     * @param key
     * @param year
     * @param adCode
     * @param branchId
     * @return
     * @throws Exception
     */
    public String exportKRAReport(String key, String year, String adCode, String branchId) throws Exception {
    	try {
    		// get columns report
            ColumnReport[] columns = null;
            if ("KRA_AGENT_QUERY".equals(key)) {
            	columns = ReportProperties.getColumnsKRAAgentReport(year.substring(2, 4));
            } else if ("KRA_BANCA_QUERY".equals(key)) {
            	columns = ReportProperties.getColumnsKRABancaReport(year.substring(2, 4));
            }
            // get list object report
            // prepare object parameter
            Map<String, Object> params = new HashMap<>();
            params.put("key", key);
            params.put("year", year);
            params.put("branchId", branchId);
            params.put("adCode", adCode);
            params.put("code", adCode);
            List<Map<String, Object>> lstReult = odsQueryService.query(params);
            for (Map<String, Object> map : lstReult) {
            	final String kra = (String)map.get("kra");
            	String subfix = (String)map.get("kra");
            	String subcate = (String) map.get("subcate");
            	String category = (String) map.get("category");
            	if (subfix.indexOf(".") > 0) {
                    subfix = subfix.substring(subfix.indexOf(".") + 1).trim();
                }
                subfix = subfix.replace(" (mil VND)", "");
                String result = subfix + " - " + subcate.replace("-", "").replace("-", "") + " (" + category.toUpperCase() + ")";
                if (result.equals("APE Actual/Plan - YTD (Actual)")) {
            		result = "APE - YTD (Actual/Plan)";
            	}
                if (isKraPercent(kra)) {
                	result += " %";
                }
                map.put("kra", result);
                
                map.put("jan", formatValueKRA(((BigDecimal) map.get("jan")).doubleValue(), 1, kra));
                map.put("feb", formatValueKRA(((BigDecimal) map.get("feb")).doubleValue(), 1, kra));
                map.put("mar", formatValueKRA(((BigDecimal) map.get("mar")).doubleValue(), 1, kra));
                map.put("apr", formatValueKRA(((BigDecimal) map.get("apr")).doubleValue(), 1, kra));
                map.put("may", formatValueKRA(((BigDecimal) map.get("may")).doubleValue(), 1, kra));
                map.put("jun", formatValueKRA(((BigDecimal) map.get("jun")).doubleValue(), 1, kra));
                map.put("jul", formatValueKRA(((BigDecimal) map.get("jul")).doubleValue(), 1, kra));
                map.put("aug", formatValueKRA(((BigDecimal) map.get("aug")).doubleValue(), 1, kra));
                map.put("sep", formatValueKRA(((BigDecimal) map.get("sep")).doubleValue(), 1, kra));
                map.put("oct", formatValueKRA(((BigDecimal) map.get("oct")).doubleValue(), 1, kra));
                map.put("nov", formatValueKRA(((BigDecimal) map.get("nov")).doubleValue(), 1, kra));
                map.put("dec", formatValueKRA(((BigDecimal) map.get("dec")).doubleValue(), 1, kra));
            }
            // create file Path Excel temporary
            String filePath = getFilePathTempNotExist(key, year + "_" + adCode);
            // write to excel file temporary
            return writeFileExcelReport(columns, lstReult, filePath, numberPatternSubmission, "###,##0.0");
		} catch (Exception e) {
			log.error(e.getMessage());
			throw e;
		}
    }
    
    /**
     * method get file path temporary
     * @param reportType
     * @param dateReport
     * @return
     * @throws Exception
     */
    private String getFilePathTempNotExist(String reportType, String dateReport) throws Exception {
    	try {
    		String filePath = folderTemp + File.separator + "report_" + reportType + "_" + dateReport + "_" + ".xlsx";
            File fileTemp = new File(filePath);
            if (fileTemp.exists()) {
                for (int i = 1; i < Integer.MAX_VALUE; i++) {
                    filePath = folderTemp + File.separator + "report_" + reportType + "_" + dateReport + "_" + "(" + i + ").xlsx";
                    File fileTmpIndex = new File(filePath);
                    if (!fileTmpIndex.exists()) {
                        break;
                    }
                }
            }
            return filePath;
		} catch (Exception e) {
			log.error(e.getMessage());
			throw e;
		}
    }
    
    /**
     * method check kra is percent
     * @param kra
     * @return
     * @throws Exception
     */
    private boolean isKraPercent(String kra) throws Exception {
    	try {
    		return kra.toUpperCase().indexOf("RATIO") > 0 || kra.toUpperCase().indexOf("ACTUAL/PLAN") > 0;
		} catch (Exception e) {
			log.error(e.getMessage());
			throw e;
		}
    }
    
    /**
     * method check kra is millions
     * @param kra
     * @return
     * @throws Exception
     */
    private boolean isKraMillions(String kra) throws Exception {
    	try {
    		return kra.toUpperCase().indexOf("CASE SIZE") > 0 || kra.toUpperCase().indexOf("MIL") > 0;
		} catch (Exception e) {
			log.error(e.getMessage());
			throw e;
		}
    }
    
    /**
     * method format data KRA
     * @param input
     * @param places
     * @return
     * @throws Exception
     */
    private double formatValueKRA(double input, int places, String kra) throws Exception {
    	try {
    		if (isKraMillions(kra)) {
    			input = (double)input/1000000;
    		}
	    	BigDecimal bd = new BigDecimal(input);
	        bd = bd.setScale(places, RoundingMode.HALF_UP);
	        return bd.doubleValue();
    	} catch (Exception e) {
			log.error(e.getMessage());
			throw e;
		}
    }
    
}
