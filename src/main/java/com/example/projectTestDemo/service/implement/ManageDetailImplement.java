package com.example.projectTestDemo.service.implement;

import com.example.projectTestDemo.dtoRequest.CreateAccountRequest;
import com.example.projectTestDemo.dtoRequest.ExportExcelRequest;
import com.example.projectTestDemo.dtoRequest.LoginRequest;
import com.example.projectTestDemo.dtoResponse.ImportExcelManageUserResponse;
import com.example.projectTestDemo.dtoResponse.Response;
import com.example.projectTestDemo.dtoResponse.ValidateSchemaResponse;
import com.example.projectTestDemo.entity.MangePeopleDetail;
import com.example.projectTestDemo.entity.ManageUser;
import com.example.projectTestDemo.environment.Constant;
import com.example.projectTestDemo.exception.ResponseException;
import com.example.projectTestDemo.queue.Push;
import com.example.projectTestDemo.repository.ManagePeopleDetailRepository;
import com.example.projectTestDemo.repository.UserRepository;
import com.example.projectTestDemo.response.custom.ResponseMapper;
import com.example.projectTestDemo.service.ManageDetailService;
import com.example.projectTestDemo.service.validation.ValidationAbstract;
import com.example.projectTestDemo.service.validation.ValidatorFactory;
import com.example.projectTestDemo.service.validation.custom.ValidatorSchema;
import com.example.projectTestDemo.tools.UtilityTools;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.StaxDriver;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;


import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;


@Service
public class ManageDetailImplement implements ManageDetailService {
    private static final Logger logger = Logger.getLogger(ManageDetailImplement.class);
    private final ManagePeopleDetailRepository managePeopleDetailRepository;
    private final UserRepository userRepository;
    private final JwtImplement jwtImplement;
    private RabbitTemplate rabbitTemplate;
    private ValidatorSchema validatorSchema;
    private ResponseMapper responseMapper;
    private ValidatorFactory validatorFactory;

    @Autowired
    public ManageDetailImplement(ManagePeopleDetailRepository managePeopleDetailRepository,
            UserRepository userRepository,RabbitTemplate rabbitTemplate,JwtImplement jwtImplement,ValidatorSchema validatorSchema,ResponseMapper responseMapper,ValidatorFactory validatorFactory) {
        this.managePeopleDetailRepository = managePeopleDetailRepository;
        this.userRepository = userRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.jwtImplement = jwtImplement;
        this.validatorSchema = validatorSchema;
        this.responseMapper = responseMapper;
        this.validatorFactory = validatorFactory;
    }

    @Value("${jwt.secretkey}")
    private String jwtSecretkey;

    @Override
    public String generateXml() throws JsonProcessingException {
        MangePeopleDetail mangePeopleDetail = this.managePeopleDetailRepository.findByRefNo("PE68");
        // Solution 1
        // XmlMapper xmlMapper = new XmlMapper();
        // String personXml = xmlMapper.writeValueAsString(mangePeopleDetail);
        XStream xstream = new XStream(new StaxDriver());
        // Solution2
        String dataXml = xstream.toXML(mangePeopleDetail);
        return dataXml;
    }

    @Override
    public Response createAccount(CreateAccountRequest createAccountRequest) {
        logger.info("===== Start CreateAccount =======");
        logger.info("username : " + createAccountRequest.getUsername());
        MangePeopleDetail mangePeopleDetail = this.managePeopleDetailRepository.findById(Integer.parseInt(createAccountRequest.getPeopleDetailId()));
        try {
            UtilityTools utilityTools = new UtilityTools();
            Boolean checkObject = ObjectUtils.isEmpty(createAccountRequest);
            Boolean checkObject2 = ObjectUtils.isEmpty(mangePeopleDetail);

            if (!checkObject && !checkObject2) {
                logger.info("MangePeopleDetail FirstName: " + mangePeopleDetail.getManagePeopleName());
                logger.info("MangePeopleDetail LastName: " + mangePeopleDetail.getManagePeopleLastName());

                if (createAccountRequest.getPassword().equals(createAccountRequest.getConfirmPassword())) {
                    ManageUser user = new ManageUser();
                    String password = utilityTools.hashSha256(createAccountRequest.getPassword());
                    String username = "US" + createAccountRequest.getUsername();
                    user.setUsername(username);
                    user.setPassword(password);
                    user.setUcode(utilityTools.randomString(10));
                    user.setCreateDate(new UtilityTools().getFormatsDateMilli());
                    user.setEmail(mangePeopleDetail.getEmail());
                    user.setApprovedDate(new UtilityTools().getFormatsDateMilli());
                    user.setFirstName(mangePeopleDetail.getManagePeopleName());
                    user.setLastName(mangePeopleDetail.getManagePeopleLastName());
                    user.setApproved("Y");
                    user.setStatusLogin("Y");
                    user.setPostCode(mangePeopleDetail.getPostCode());
                    user.setCreateBy(BigInteger.valueOf(0));
                    user.setForceChangePassword(1);
                    user.setToken_user(jwtImplement.generate(username, mangePeopleDetail.getEmail(), mangePeopleDetail.getManagePeopleTaxId()));
                    this.userRepository.save(user);
                } else {
                    return new Response(false, "รหัสผ่านระบุไม่ถูกต้อง เนื่องจาก ระบุรหัสผ่านไม่ตรงกับยืนยันรหัสผ่าน",
                            "500");
                }
            } else {
                return new Response(false, "สร้างบัญชีผู้ใช้งานไม่สำเร็จ", "500");
            }
        } catch (ResponseException | NoSuchAlgorithmException | UnsupportedEncodingException | ParseException e) {
            logger.error("error : "+ e.getMessage());
            return new Response(false, "สร้างบัญชีผู้ใช้งานไม่สำเร็จ", "500");
        }
        logger.info("===== End CreateAccount =======");
        return new Response(true, "สร้างบัญชีผู้ใช้งานเสร็จเรียบร้อย", "500");
    }

    @Override
    public String gen() {
        return jwtImplement.generate("test", "2", "001");
    }

    @Override
    public Response login(LoginRequest loginRequest) {
        logger.info("===== Start Login =======");
        logger.info("username : " + loginRequest.getUsername());
        UtilityTools utilityTools = new UtilityTools();

        try {
            ManageUser manageUser = this.userRepository.findByUsername(loginRequest.getUsername());

            Boolean checkPass = true;
            Boolean checkObject = ObjectUtils.isEmpty(manageUser);
            logger.info("checkObject : " + checkObject);
            if (!checkObject) {
                checkPass = utilityTools.checkPassphrases(manageUser.getPassword(),
                        loginRequest.getPassword());
                logger.info("checkpass : " + checkPass);
                if (!checkPass) {
                    return new Response(false, "บัญชีผู้ใช้งานหรือ รหัสผ่านไม่ถูกต้องโปรดตรวจสอบ", "500");
                }
            } else {
                return new Response(false, "บัญชีผู้ใช้งานไม่มีในระบบ", "500");
            }

        } catch (ResponseException | NoSuchAlgorithmException | UnsupportedEncodingException e) {
            logger.error("error : "+ e.getMessage());
            return new Response(false, "เข้าสู่ระบบไม่สำเร็จ", "500");
        }
        logger.info("===== End Login =======");
        return new Response(true, "เข้าสู่ระบบสำเร็จ", "200");
    }

    @Override
    public void exportExcel(ExportExcelRequest exportExcelRequest, HttpServletResponse response) {
        try {
            this.exportSearchUserByApproved(response, exportExcelRequest);
        } catch (Exception e) {
            logger.error("error : "+ e.getMessage());
        }
    }

    @Override
    public ImportExcelManageUserResponse importExcel(MultipartFile file) throws IOException {
        ImportExcelManageUserResponse manageUserList;
        if(("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").equals(file.getContentType())){
             manageUserList = this.importDataExcel(file);
        }else {
            return new ImportExcelManageUserResponse(false, "import fail Because file not Excel", "500",null);

        }
        return manageUserList;
    }

    @Override
    public Response sendMessageToQueue() {
        MangePeopleDetail mangePeopleDetail = this.managePeopleDetailRepository.findById(1);

        try {
            if(mangePeopleDetail != null){
                Push push = new Push(rabbitTemplate);
                push.requestUpdateManage(mangePeopleDetail);
            }
        }catch (ResponseException e){
            logger.error("error : "+ e.getMessage());
            return new Response(false, "ส่งไม่สำเร็จ", "500");
        }
        return new Response(true, "ส่งสำเร็จ", "200");
    }

    @Override
    public Response testValidationLoginRequest(String jsonRequest) {
        UtilityTools utilityTools = new UtilityTools();
        ObjectMapper objectMapper = new ObjectMapper();

        ValidationAbstract validator = validatorFactory.getValidator(Constant.REQUEST_LOGIN);
        ValidateSchemaResponse validateSchemaResponse = validator.validate(Constant.REQUEST_LOGIN, jsonRequest);

        try {
            if(validateSchemaResponse.isStatus()){
                LoginRequest loginRequest = objectMapper.readValue(jsonRequest,LoginRequest.class);
                ManageUser manageUser = this.userRepository.findByUsername(loginRequest.getUsername());
                //map response to file response.vm
                String jsonResponse = responseMapper.mapSpResponseToJson(Constant.REQUEST_LOGIN, objectMapper.writeValueAsString(manageUser));
                Gson gson = new GsonBuilder().serializeNulls().create();
                logger.info("jsonResponse" + gson.toJson(jsonResponse));

                Boolean checkPass = true;
                Boolean checkObject = ObjectUtils.isEmpty(manageUser);
                logger.info("checkObject : " + checkObject);
                if (!checkObject) {
                    checkPass = utilityTools.checkPassphrases(manageUser.getPassword(),
                            loginRequest.getPassword());
                    logger.info("checkpass : " + checkPass);
                    if (!checkPass) {
                        return new Response(false, "บัญชีผู้ใช้งานหรือ รหัสผ่านไม่ถูกต้องโปรดตรวจสอบ", "500");
                    }
                } else {
                    return new Response(false, "บัญชีผู้ใช้งานไม่มีในระบบ", "500");
                }
            }else {
                return new Response(false, validateSchemaResponse.getMessage(), "500");
            }
        }catch (ResponseException | JsonProcessingException | UnsupportedEncodingException | NoSuchAlgorithmException e){
            logger.error("error : " + e.getMessage());
        }
        return new Response(true, "เข้าสู่ระบบสำเร็จ", "200");
    }

    public void exportSearchUserByApproved(HttpServletResponse response, ExportExcelRequest dto) throws IOException {
        response.setHeader("Content-Type", "application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=" + "keyword_master" + ".xlsx");
        OutputStream outStream = null;

        try {
            // fix column name
            String[] columns = {
                    "UID",
                    "FirstName",
                    "LastName"
            };

            Workbook workbook = new XSSFWorkbook();

            CreationHelper createHelper = workbook.getCreationHelper();

            Sheet sheet = workbook.createSheet("master");

            // set style Header
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 14);

            CellStyle headerCellStyle = workbook.createCellStyle();
            headerCellStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);

            // create header cell
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerCellStyle);
            }

            int rowNum = 1;

            List<ManageUser> list = this.userRepository.findByApproved(dto.getApproved());

            // initialize data in row
            for (ManageUser d : list) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(d.getUid().toString());
                row.createCell(1).setCellValue(d.getFirstName());
                row.createCell(2).setCellValue(d.getLastName());
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // output response file name
            outStream = response.getOutputStream();
            workbook.write(outStream);
            outStream.flush();
            workbook.close();

        } catch (Exception e) {
            logger.error("error : "+ e.getMessage());
        } finally {
            if (outStream != null) {
                outStream.close();
            }
        }
    }

    public ImportExcelManageUserResponse importDataExcel(MultipartFile files) throws IOException {
        List<ManageUser> manageUserList = new ArrayList<>();
        XSSFWorkbook workbook = new XSSFWorkbook(files.getInputStream());
        // get sheet in Excel By index
        XSSFSheet worksheet = workbook.getSheetAt(0);
        try {
            for (int index = 0; index < worksheet.getPhysicalNumberOfRows(); index++) {
                if (index > 0) {
                    ManageUser manageUser = new ManageUser();

                    XSSFRow row = worksheet.getRow(index);

                    manageUser.setUid(new BigInteger(row.getCell(0).getStringCellValue()));
                    manageUser.setFirstName(row.getCell(1).getStringCellValue());
                    manageUser.setLastName(row.getCell(2).getStringCellValue());
                    manageUserList.add(manageUser);
                }
            }
        } catch (Exception e) {
            logger.error("error : "+ e.getMessage());
        } finally {
            workbook.close();
        }
        return new ImportExcelManageUserResponse(true, "import success", "200",manageUserList);
    }

}
