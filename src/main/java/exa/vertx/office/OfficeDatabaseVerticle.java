package exa.vertx.office;

import io.vertx.core.AbstractVerticle;

import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;

import java.util.HashMap;

import java.util.List;
import java.util.Properties;


public class OfficeDatabaseVerticle extends AbstractVerticle {

    public static final String CONFIG_EPIMDB_JDBC_URL = "epimdb.jdbc.url";
    public static final String CONFIG_EPIMDB_JDBC_DRIVER_CLASS = "epimdb.jdbc.driver_class";
    public static final String CONFIG_EPIMDB_JDBC_USER = "postgres";
    public static final String CONFIG_EPIMDB_JDBC_PASSWORD = "admin";
    public static final String CONFIG_EPIMDB_JDBC_MAX_POOL_SIZE = "epimdb.jdbc.max_pool_size";
    public static final String CONFIG_EPIMDB_SQL_QUERIES_RESOURCE_FILE = "epimdb.sqlqueries.resource.file";

    public static final String CONFIG_EPIMDB_QUEUE = "epimdb.queue";

    private static final Logger LOGGER = LoggerFactory.getLogger(OfficeDatabaseVerticle.class);

    // (...)
    // end::preamble[]

    // tag::loadSqlQueries[]
    private enum SqlQuery {
        GET_LOGIN,
        GET_USER,
        GET_MAIL,
        GET_MAIL_TO,
        /* OLD QUERY BELOW*/
        GET_INBOX,
        GET_DASHBOARD,
        GET_FLOW_DISPOS,
        GET_STAFF_LEV1,
        GET_STAFF_LEV2,
        GET_STAFF_LEV3,
        GET_STAFF,
        POST_NOTIFIKASI,
        POST_DISPOSISI,
        POST_TINDAKLANJUT,
        UPDATE_INBOX,
        UPDATE_STATUS_INBOX,

    }


    private final HashMap<SqlQuery, String> sqlQueries = new HashMap<>();

    private void loadSqlQueries() throws IOException {

        String queriesFile = config().getString(CONFIG_EPIMDB_SQL_QUERIES_RESOURCE_FILE);
        InputStream queriesInputStream;
        if (queriesFile != null) {
            queriesInputStream = new FileInputStream(queriesFile);
        } else {
            queriesInputStream = getClass().getResourceAsStream("/db-eoffice-prod.properties");
        }

        Properties queriesProps = new Properties();
        queriesProps.load(queriesInputStream);
        queriesInputStream.close();

        sqlQueries.put(SqlQuery.GET_LOGIN, queriesProps.getProperty("get-user-login"));
        sqlQueries.put(SqlQuery.GET_USER, queriesProps.getProperty("get-user-profile"));
        sqlQueries.put(SqlQuery.GET_INBOX, queriesProps.getProperty("get-inbox-byuserid"));
        sqlQueries.put(SqlQuery.GET_DASHBOARD, queriesProps.getProperty("get-dashboard"));
        sqlQueries.put(SqlQuery.GET_FLOW_DISPOS, queriesProps.getProperty("get-flow-dispos"));
        sqlQueries.put(SqlQuery.GET_STAFF_LEV1, queriesProps.getProperty("get-staff-lev1"));
        sqlQueries.put(SqlQuery.GET_STAFF_LEV2, queriesProps.getProperty("get-staff-lev2"));
        sqlQueries.put(SqlQuery.GET_STAFF_LEV3, queriesProps.getProperty("get-staff-lev3"));
        sqlQueries.put(SqlQuery.GET_STAFF, queriesProps.getProperty("get-staff"));

        /* new query remark with prefix mail*/
        sqlQueries.put(SqlQuery.GET_MAIL, queriesProps.getProperty("get-mail"));
        sqlQueries.put(SqlQuery.GET_MAIL_TO, queriesProps.getProperty("get-mail-to"));

        sqlQueries.put(SqlQuery.POST_DISPOSISI, queriesProps.getProperty("post-disposisi"));
        sqlQueries.put(SqlQuery.POST_NOTIFIKASI, queriesProps.getProperty("post-notifikasi"));
        sqlQueries.put(SqlQuery.POST_TINDAKLANJUT, queriesProps.getProperty("post-tindak-lanjut"));

        sqlQueries.put(SqlQuery.UPDATE_INBOX, queriesProps.getProperty("update-inbox"));
        sqlQueries.put(SqlQuery.UPDATE_STATUS_INBOX, queriesProps.getProperty("update-status-inbox"));


    }
    // end::loadSqlQueries[]

    // tag::start[]
    private JDBCClient dbClient;

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        loadSqlQueries();

        dbClient = JDBCClient.createShared(vertx, new JsonObject()
                .put("url", "jdbc:postgresql://localhost:5432/db_office")
                .put("user", "postgres")
                .put("password", "admin")
                .put("max_pool_size", 30)
        );

        dbClient.getConnection(ar -> {
            if (ar.failed()) {
                LOGGER.error("Could not open a database connection", ar.cause());
                startFuture.fail(ar.cause());
            } else {
                vertx.eventBus().consumer(config().getString(CONFIG_EPIMDB_QUEUE, "epimdb.queue"), this::onMessage);
                startFuture.complete();
                LOGGER.info("Connection established");

            }
        });


    }

    public void onMessage(Message<JsonObject> message) {

        if (!message.headers().contains("action")) {
            LOGGER.error("No action header specified for message with headers {} and body {}",
                    message.headers(), message.body().encodePrettily());
            message.fail(ErrorCodes.NO_ACTION_SPECIFIED.ordinal(), "No action header specified");
            return;
        }
        String action = message.headers().get("action");

        switch (action) {
            case "get-user-login":
                getUserLogin(message);
                break;
            case "get-user":
                getUser(message);
                break;
            case "get-mail":
                getMail(message);
                break;
            case "get-mail-to":
                getMailTo(message);
                break;
            case "get-inbox-byuserid":
                getInboxById(message);
                break;
            case "get-dashboard":
                getDashboardById(message);
                break;
            case "get-flow-dispos":
                getFlowDispos(message);
                break;
            case "get-staff-lev1":
                getStaffLev1(message);
                break;
            case "get-staff-lev2":
                getStaffLev2(message);
                break;
            case "get-staff-lev3":
                getStaffLev3(message);
                break;
            case "get-staff":
                getStaff(message);
                break;
            case "post-notifikasi":
                postNotifikasi(message);
                break;
            case "post-dispos-notif":
                postDisposisi(message);
                break;
            case "post-tindak-lanjut":
                postTindakLanjut(message);
                break;
            default:
                message.fail(ErrorCodes.BAD_ACTION.ordinal(), "Bad action: " + action);
        }
    }


    private void getMailTo(Message<JsonObject> message) {
        String mailId = message.body().getString("mailId");
        JsonArray params = new JsonArray().add(mailId).add(mailId);

        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_MAIL_TO),params, res -> {
            if (res.succeeded()) {
                JsonArray arr = new JsonArray();
                res.result().getRows().forEach(arr::add);

                message.reply(new JsonObject().put("mail", arr));


            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void getMail(Message<JsonObject> message) {
        String nip = message.body().getString("nip");
        String type = message.body().getString("type");
        JsonArray params = new JsonArray().add(nip).add(type);


        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_MAIL),params, res -> {
            if (res.succeeded()) {
                JsonArray arr = new JsonArray();
                res.result().getRows().forEach(arr::add);

                message.reply(new JsonObject().put("mail", arr));


            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void postTindakLanjut(Message<JsonObject> message) {
        JsonObject jsonData = message.body().getJsonObject("data");
        Integer id_dispos = jsonData.getInteger("id");
        Integer id_surat = jsonData.getInteger("id_surat");
        String isi = jsonData.getString("isi");

        LocalDate localTime = LocalDate.now();


        JsonArray params = new JsonArray().add(id_dispos).add(isi).add(348).add(localTime.toString()).add(localTime.toString());

        JsonArray paramsUpdate = new JsonArray().add(2).add(id_surat);


        dbClient.updateWithParams(sqlQueries.get(SqlQuery.UPDATE_STATUS_INBOX),paramsUpdate,resUpdate ->{
            if (resUpdate.succeeded()){
                dbClient.updateWithParams(sqlQueries.get(SqlQuery.POST_TINDAKLANJUT),params,res ->{
                    if (res.succeeded()){
                        message.reply(new JsonObject().put("postTindakLanjut","succeed"));
                    }  else {
                        reportQueryError(message, res.cause());
                    }
                });
                message.reply(new JsonObject().put("update","succeed"));
            } else {
                reportQueryError(message, resUpdate.cause());
            }

        });


    }


    private void getStaffLev3(Message<JsonObject> message) {
        String unor = message.body().getString("unor");
        JsonArray params = new JsonArray().add(unor);


        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_STAFF_LEV3),params, res -> {
            if (res.succeeded()) {
                JsonArray arr = new JsonArray();
                res.result().getRows().forEach(arr::add);

                message.reply(new JsonObject().put("unor", arr));


            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void getStaffLev2(Message<JsonObject> message) {
        String unor = message.body().getString("unor");
        JsonArray params = new JsonArray().add(unor);


        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_STAFF_LEV2),params, res -> {
            if (res.succeeded()) {
                JsonArray arr = new JsonArray();
                res.result().getRows().forEach(arr::add);

                message.reply(new JsonObject().put("unor", arr));


            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void postNotifikasi(Message<JsonObject> message) {
    }

    private void postDisposisi(Message<JsonObject> message) {
        JsonObject disposNotif = message.body().getJsonObject("data");
        Integer id_surat = disposNotif.getInteger("id_surat");
       // Integer id_asal = disposNotif.getInteger("id_asal");
        Integer id_asal = Integer.parseInt(disposNotif.getString("id_asal"));
        Integer id_tujuan = Integer.parseInt(disposNotif.getString("id_tujuan"));
        String isi = disposNotif.getString("isi");

        JsonArray nip = disposNotif.getJsonArray("nip");
        LocalDate localTime = LocalDate.now();

        JsonArray paramsInsert = new JsonArray()
                .add(id_surat)
                .add(id_tujuan)
                .add(id_asal)
                .add(isi)
                .add(0)
                .add(0)
                .add(localTime.toString())
                .add(localTime.toString())
                .add(localTime.toString())
                .add(0)
                .add(348)
                .add(1)
                .add(isi);

        JsonArray paramsUpdate = new JsonArray()
                .add(id_asal)
                .add(id_tujuan)
                .add(1)
                .add(id_surat);


        dbClient.updateWithParams(sqlQueries.get(SqlQuery.POST_DISPOSISI),paramsInsert,resInsert ->{
            if (resInsert.succeeded()){

                dbClient.updateWithParams(sqlQueries.get(SqlQuery.UPDATE_INBOX),paramsUpdate,res ->{
                    if (res.succeeded()){

                        // start insert data into table notifikasi
                        nip.forEach(result -> {
                            JsonArray params = new JsonArray()
                                    .add(id_asal)
                                    .add(id_tujuan)
                                    .add(id_surat)
                                    .add("SURAT_MASUK")
                                    .add(0)
                                    .add(localTime.toString())
                                    .add(localTime.toString())
                                    .add(result);

                            dbClient.updateWithParams(sqlQueries.get(SqlQuery.POST_NOTIFIKASI),params,resInsertNotif ->{
                                if (res.succeeded()){
                                    message.reply(new JsonObject().put("postNotif","succeed"));
                                }  else {
                                    reportQueryError(message, resInsertNotif.cause());
                                }
                            });
                        });
                        message.reply(new JsonObject().put("update disposisi","succeed"));

                        // end tag[insert data into table notifikasi]
                    }else{
                        reportQueryError(message, res.cause());
                    }

                });
                message.reply(new JsonObject().put("postDisposisi","succeed"));
            }else {
                reportQueryError(message, resInsert.cause());
            }
        });

    }

    private void getStaff(Message<JsonObject> message) {
        String unor = message.body().getString("unor");

        JsonArray params = new JsonArray().add(unor);


        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_STAFF),params, res -> {
            if (res.succeeded()) {
                JsonArray arr = new JsonArray();
                res.result().getRows().forEach(arr::add);

                message.reply(new JsonObject().put("unor", arr));


            } else {
                reportQueryError(message, res.cause());
            }
        });
       // dbClient.close();
    }

    private void getStaffLev1(Message<JsonObject> message) {
        String unor = message.body().getString("unor");
        String level = message.body().getString("level");
        JsonArray params = new JsonArray().add(unor).add(level);


        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_STAFF_LEV1),params, res -> {
            if (res.succeeded()) {
                JsonArray arr = new JsonArray();
                res.result().getRows().forEach(arr::add);

                message.reply(new JsonObject().put("unor", arr));


            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void getFlowDispos(Message<JsonObject> message) {

        String idSurat = message.body().getString("idsurat");

        JsonArray params = new JsonArray().add(idSurat);

        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_FLOW_DISPOS),params, res -> {
            if (res.succeeded()) {
                JsonArray arr = new JsonArray();
                res.result().getRows().forEach(arr::add);

                message.reply(new JsonObject().put("flowDispos", arr));


            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void getDashboardById(Message<JsonObject> message) {
        String nip = message.body().getString("nip");

        JsonArray params = new JsonArray().add(nip).add(nip).add(nip);


        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_DASHBOARD),params, res -> {
            if (res.succeeded()) {
                JsonArray arr = new JsonArray();
                res.result().getRows().forEach(arr::add);

                message.reply(new JsonObject().put("dashboard", arr));


            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void getInboxById(Message<JsonObject> message) {

        String nip = message.body().getString("nip");


        JsonArray params = new JsonArray().add(nip);
        // tag::query-simple-oneshot[]
        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_INBOX),params, res -> {
            if (res.succeeded()) {
                JsonArray arr = new JsonArray();
                res.result().getRows().forEach(arr::add);

                message.reply(new JsonObject().put("inbox", arr));
            } else {
                reportQueryError(message, res.cause());
            }
        });
        // end::query-simple-oneshot[]
    }

    private void getUserLogin(Message<JsonObject> message) {
        JsonObject credential = message.body().getJsonObject("session");

        LOGGER.info("/getuserlogin :" + credential);

        String username = message.body().getJsonObject("session").getString("username");
        LOGGER.info("/username : "+username);
        String user = credential.getString("username");
        String pwd  = credential.getString("password");

        LOGGER.info("user/pwd db: "+user+"/"+pwd);

        JsonArray params = new JsonArray().add(user);

        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_LOGIN),params, res -> {
            if (res.succeeded()) {
                LOGGER.info("rows : "+res.result().getNumRows());

                if (res.result().getNumRows() >= 1){
                    String candidate = res.result().getRows().get(0).getString("pega_password").replaceFirst("2y", "2a");

                    if (BCrypt.checkpw(pwd,candidate)) {

                        LOGGER.info("password matched");

                        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_USER),params, resProfile -> {
                            if (resProfile.succeeded()){
                                JsonArray arr = new JsonArray();
                                resProfile.result().getRows().forEach(arr::add);

                                message.reply(new JsonObject().put("epim",arr).put("success","true"));

                            }else{
                                reportQueryError(message, resProfile.cause());
                            }

                        });
                    }else{
                        //reportQueryError(message, res.cause());
                        message.reply(new JsonObject().put("success","false"));
                    }
                } else{
                    message.reply(new JsonObject().put("success","false"));
                }


            } else {
                //message.reply(new JsonObject().put("db-reply","user or password doesn't matched."));
                reportQueryError(message, res.cause());
            }
        });
    }


    private void getUser(Message<JsonObject> message) {

        String user = message.body().getString("username");
        String pwd  = message.body().getString("password");

        JsonArray params = new JsonArray().add(user);

        System.out.println("user : "+user+"/"+pwd);

        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_USER),params, res -> {
            if (res.succeeded()) {
                JsonArray arr = new JsonArray();
                res.result().getRows().forEach(arr::add);


                List<JsonObject> rows = res.result().getRows();

                String pwdHash=null;

                for (JsonObject row : rows) {

                    pwdHash = row.getString("0");
                    System.out.println("row : "+row.getString("0"));
                }


                System.out.println("pwdHash ; "+pwdHash);

                message.reply(new JsonObject().put("data", arr));

               /* String candidate = res.result().getRows().get(0).getString("pega_password").replaceFirst("2y", "2a");

                if (BCrypt.checkpw(pwd,candidate)) {

                    JsonArray arr = new JsonArray();
                    res.result().getRows().forEach(arr::add);

                    message.reply(new JsonObject().put("profile", arr));
                }else{
                    message.reply(new JsonObject().put("user-reply","user or password doesn't matched."));
                }*/

            } else {
                reportQueryError(message, res.cause());
            }
        });

    }
    // end::onMessage[]






    // tag::onMessage[]
    public enum ErrorCodes {
        NO_ACTION_SPECIFIED,
        BAD_ACTION,
        DB_ERROR
    }

    private void reportQueryError(Message<JsonObject> message, Throwable cause) {
        LOGGER.error("Database query error", cause);
        message.fail(ErrorCodes.DB_ERROR.ordinal(), cause.getMessage());
    }
}
