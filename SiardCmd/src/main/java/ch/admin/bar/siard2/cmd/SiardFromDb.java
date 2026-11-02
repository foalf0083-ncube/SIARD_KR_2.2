// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import ch.admin.bar.siard2.cmd.utils.CmdI18n;
import ch.admin.bar.siard2.api.Archive;
import ch.admin.bar.siard2.api.MetaColumn;
import ch.admin.bar.siard2.api.MetaSchema;
import ch.admin.bar.siard2.api.MetaTable;
import ch.admin.bar.siard2.api.primary.ArchiveImpl;
import ch.admin.bar.siard2.cmd.utils.RuntimeHelper;
import ch.admin.bar.siard2.cmd.utils.VersionsExplorer;
import ch.enterag.utils.EU;
import ch.enterag.utils.ProgramInfo;
import ch.enterag.utils.background.Progress;
import ch.enterag.utils.cli.Arguments;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SiardFromDb
{
  private static final Logger LOG = LoggerFactory.getLogger(SiardFromDb.class);
  
  public static final int iRETURN_OK = 0;
  
  public static final int iRETURN_WARNING = 4;
  public static final int iRETURN_ERROR = 8;
  public static final int iRETURN_FATAL = 12;
  private static final ProgramInfo _pi = ProgramInfo.getProgramInfo("SIARD Suite", VersionsExplorer.INSTANCE
                  .getSiardVersion(), "SiardFromDb", VersionsExplorer.INSTANCE
                  .getAppVersion(), "Program to store database content in a .siard file", "Swiss Federal Archives, Berne, Switzerland, 2008-2016",
          Arrays.asList("Hartwig Thomas, Enter AG, Rüti ZH, Switzerland", "Andreas Voss, Swiss Federal Archives, Berne, Switzerland", "Anders Bo Nielsen, Danish National Archives, Denmark", "Claire Röthlisberger-Jourdan, KOST, Berne, Switzerland"),
          Arrays.asList("Hartwig Thomas, Enter AG, Rüti ZH, Switzerland", "Simon Jutz, Cytex GmbH, Zurich, Switzerland"),
          Arrays.asList("Claudia Matthys, POOL Computer AG, Zurich, Switzerland", "Marcel Büchler, Swiss Federal Archives, Berne, Switzerland", "Yvan Dutoit, Swiss Federal Archives, Berne, Switzerland"),
          Arrays.asList("Hartwig Thomas, Enter AG, Rüti ZH, Switzerland", "Marcel Büchler, Swiss Federal Archives, Berne, Switzerland", "Alain Mast, Swiss Federal Archives, Berne, Switzerland", "Krystyna Ohnesorge, Swiss Federal Archives, Berne, Switzerland"));
  
  private boolean _bOverwrite = false;
  private boolean _bViewsAsTables = false;
  private int _iLoginTimeoutSeconds = 300;
  private int _iQueryTimeoutSeconds = 75;
  private File _fileImportXml = null;
  private File _fileExternalLobFolder = null;
  private URI _uriExternalLobFolder = null;
  private String _sMimeType = null;
  private String _sJdbcUrl = null;
  private String _sDatabaseUser = null;
  private String _sDatabasePassword = null;
  private File _fileSiard = null;
  private File _fileExportXml = null;
  
  private Archive _archive = null;
  private Connection _conn = null;
  
  private int _iReturn = 4;
  
  public int getReturn() {
    return this._iReturn;
  }

  private void printUsage() {
    System.out.println(CmdI18n.get("cmd.common.usage"));
    System.out.println(CmdI18n.get("cmd.fromDb.javaCpSiardpathLibSiardcmdJar"));
    System.out.println(CmdI18n.get("cmd.fromDb.oVLLoginTimeoutQ"));
    
    System.out.println(CmdI18n.get("cmd.fromDb.jJdbcUrlUDatabaseUser"));
    
    System.out.println(CmdI18n.get("cmd.common.where"));
    System.out.println(CmdI18n.get("cmd.fromDb.siardpathInstallationPathOfSiardcmd"));
    System.out.println(CmdI18n.get("cmd.fromDb.hHelpPrintsThisUsageInformation"));
    System.out.println(CmdI18n.get("cmd.fromDb.oOverwriteOverwriteExistingSiardFile"));
    System.out.println(CmdI18n.get("cmd.fromDb.vViewsAsTablesArchiveViews"));
    System.out.println(CmdI18n.get("cmd.fromDb.loginTimeoutLoginTimeoutInSeconds", this._iLoginTimeoutSeconds));
    
    System.out.println(CmdI18n.get("cmd.fromDb.queryTimeoutQueryTimeoutInSeconds", this._iQueryTimeoutSeconds));
    
    System.out.println(CmdI18n.get("cmd.fromDb.importXmlNameOfMetaData"));
    System.out.println(CmdI18n.get("cmd.fromDb.lobFolderFolderForStoringLargest"));
    System.out.println(CmdI18n.get("cmd.fromDb.contentsWillBeDeletedIfThey"));
    System.out.println(CmdI18n.get("cmd.fromDb.mimeTypeMimeTypeOfData"));
    System.out.println(CmdI18n.get("cmd.fromDb.jdbcUrlJdbcUrlOfDatabase"));
    System.out.print("                       e.g. ");
    SiardConnection sc = SiardConnection.getSiardConnection();
    String[] asSchemes = sc.getSchemes();
    for (int i = 0; i < asSchemes.length; i++) {
      if (i > 0) System.out.println(CmdI18n.get("cmd.common.msg")); 
      String sScheme = asSchemes[i];
      System.out.println(CmdI18n.get("cmd.common.for", sc.getTitle(sScheme), sc.getSampleUrl(sScheme, "dbserver.enterag.ch", "D:\\dbfolder", "testdb")));
    }
    System.out.println(CmdI18n.get("cmd.fromDb.databaseUserDatabaseUser"));
    System.out.println(CmdI18n.get("cmd.fromDb.databasePasswordDatabasePassword"));
    System.out.println(CmdI18n.get("cmd.fromDb.siardFileNameOfSiardFile"));
    System.out.println(CmdI18n.get("cmd.fromDb.exportXmlNameOfMetaData"));
  }

  private void getParameters(String[] asArgs) {
    this._iReturn = 0;
    Arguments args = Arguments.newInstance(asArgs);
    if (args.getOption("h") != null) this._iReturn = 4;
    
    if (args.getOption("o") != null) this._bOverwrite = true;
    
    if (args.getOption("v") != null) this._bViewsAsTables = true;
    
    String sLoginTimeoutSeconds = args.getOption("l");
    
    String sQueryTimeoutSeconds = args.getOption("q");
    
    String sImportXml = args.getOption("i");
    
    String sExportXml = args.getOption("e");
    
    String sExternalLobFolder = args.getOption("x");
    
    this._sMimeType = args.getOption("m");
    
    this._sJdbcUrl = args.getOption("j");
    
    this._sDatabaseUser = args.getOption("u");
    
    this._sDatabasePassword = args.getOption("p");
    
    String sSiardFile = args.getOption("s");
    
    if (this._iReturn == 0) {
      if (sSiardFile != null) this._fileSiard = new File(sSiardFile); 
      if (sExportXml != null) this._fileExportXml = new File(sExportXml);
      
      if (sSiardFile == null && sExportXml == null) {
        System.out.println(CmdI18n.get("cmd.fromDb.siardFileAndOrExportMeta"));
        this._iReturn = 8;
      } 
      if (sLoginTimeoutSeconds != null) {
        try {
          this._iLoginTimeoutSeconds = Integer.parseInt(sLoginTimeoutSeconds);
        } catch (NumberFormatException nfe) {
          System.out.println(CmdI18n.get("cmd.common.invalidLoginTimeout", sLoginTimeoutSeconds));
          this._iReturn = 8;
        } 
      }
      if (sQueryTimeoutSeconds != null) {
        try {
          this._iQueryTimeoutSeconds = Integer.parseInt(sQueryTimeoutSeconds);
        } catch (NumberFormatException nfe) {
          System.out.println(CmdI18n.get("cmd.common.invalidQueryTimeout", sQueryTimeoutSeconds));
          this._iReturn = 8;
        } 
      }
      if (sImportXml != null) {
        this._fileImportXml = new File(sImportXml);
        if (!this._fileImportXml.isFile() || !this._fileImportXml.exists()) {
          System.out.println(CmdI18n.get("cmd.fromDb.fileImportXmlDoesNotExist", this._fileImportXml.getAbsolutePath()));
          this._iReturn = 8;
        } 
      } 
      if (sExternalLobFolder != null) {
        this._fileExternalLobFolder = new File(sExternalLobFolder);
        if (!this._fileExternalLobFolder.exists() || !this._fileExternalLobFolder.isDirectory()) {
          System.out.println(CmdI18n.get("cmd.fromDb.externalLobFolderDoesNotExist", this._fileExternalLobFolder.getAbsolutePath()));
          this._iReturn = 8;
        } else {
          if (this._fileSiard.getParentFile() == null) {
            System.out.println(CmdI18n.get("cmd.fromDb.toUseExternalLobFolderSpecify", this._fileSiard));
            this._iReturn = 8;
          } 




          
          File fileRelative = this._fileSiard.getParentFile().getAbsoluteFile().toPath().relativize(this._fileExternalLobFolder.getAbsoluteFile().toPath()).toFile();
          
          try {
            this._uriExternalLobFolder = new URI("../" + fileRelative + "/");
          } catch (URISyntaxException use) {
            System.out.println(CmdI18n.get("cmd.fromDb.externalLobFolderCouldNotBe", this._fileExternalLobFolder.getAbsolutePath()));
            this._iReturn = 8;
          } 
        } 
      } 
      
      String sError = SiardConnection.getSiardConnection().loadDriver(this._sJdbcUrl);
      if (sError != null) {
        System.out.println(CmdI18n.get("cmd.fromDb.jdbcUrlIsNotValid", this._sJdbcUrl));
        System.out.println(sError);
        this._iReturn = 8;
      } 
      if (this._sDatabaseUser == null) {
        System.out.println(CmdI18n.get("cmd.common.databaseUserMustBeGiven"));
        this._iReturn = 8;
      } 
      if (this._sDatabasePassword == null) {
        System.out.println(CmdI18n.get("cmd.common.databasePasswordMustBeGiven"));
        this._iReturn = 8;
      } 
    } 
    
    if (this._iReturn == 0)
    
    { 



      
      StringBuilder sb = (new StringBuilder()).append("\n").append("Parameters\n").append("  SIARD file             : ").append(this._fileSiard.getAbsolutePath()).append("\n").append("  JDBC URL               : ").append(this._sJdbcUrl).append("\n").append("  Database user          : ").append(this._sDatabaseUser).append("\n").append("  Database password      : ***\n");
      if (this._fileSiard != null)
        sb.append("  SIARD file             : ").append(this._fileSiard.getAbsolutePath()).append("\n"); 
      if (this._fileExportXml != null)
        sb.append("  Export meta data XML   : ").append(this._fileExportXml.getAbsolutePath()).append("\n"); 
      if (sLoginTimeoutSeconds != null)
        sb.append("  Login timeout          : ").append(this._iLoginTimeoutSeconds).append(" seconds\n"); 
      if (sQueryTimeoutSeconds != null)
        sb.append("  Query timeout          : ").append(this._iQueryTimeoutSeconds).append(" seconds\n"); 
      if (this._fileImportXml != null)
        sb.append("  Import meta data XML   : ").append(this._fileImportXml.getAbsolutePath()).append("\n"); 
      if (this._uriExternalLobFolder != null)
        sb.append("  External LOB folder    : ").append(this._uriExternalLobFolder).append("\n"); 
      if (this._sMimeType != null)
        sb.append("  External LOB MIME type : ").append(this._sMimeType).append("\n"); 
      if (this._bViewsAsTables)
        sb.append("  Archive views as tables: ").append(this._bViewsAsTables).append("\n"); 
      sb.append("\n");
      
      String message = sb.toString();
      LOG.info(message);
      System.out.println(message); }
    else { printUsage(); }
  
  }





  
  public SiardFromDb(String[] asArgs) throws SQLException, IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
    getParameters(asArgs);
    if (this._iReturn == 0) {
      if (this._bOverwrite) {
        if (this._fileSiard != null && 
          this._fileSiard.exists()) this._fileSiard.delete();
        
        if (this._fileExportXml != null && 
          this._fileExportXml.exists()) this._fileExportXml.delete();
      
      } 
      if ((this._fileSiard == null || !this._fileSiard.exists()) && (this._fileExportXml == null || !this._fileExportXml.exists()))
      
      { String sError = null;
        
        SiardConnection siardConnection = SiardConnection.getSiardConnection();
        if (sError == null || sError.length() == 0)
        { DriverManager.setLoginTimeout(this._iLoginTimeoutSeconds);
          this._conn = siardConnection.createValidConnection(this._sJdbcUrl, this._sDatabaseUser, this._sDatabasePassword);
          if (this._conn != null && !this._conn.isClosed())
          { System.out.println(CmdI18n.get("cmd.common.connectedTo", this._conn.getMetaData().getURL()));
            this._conn.setAutoCommit(false);
            
            this._archive = ArchiveImpl.newInstance();
            File fileSiard = this._fileSiard;
            if (fileSiard == null) {
              fileSiard = File.createTempFile("siard", ".siard");
              fileSiard.delete();
            } 
            this._archive.create(fileSiard);
            if (this._fileImportXml != null) {
              FileInputStream fis = new FileInputStream(this._fileImportXml);
              this._archive.importMetaDataTemplate(fis);
              fis.close();
            } 
            
            MetaDataFromDb mdfd = MetaDataFromDb.newInstance(this._conn.getMetaData(), this._archive.getMetaData());
            mdfd.setQueryTimeout(this._iQueryTimeoutSeconds);
            mdfd.download(this._bViewsAsTables, (this._uriExternalLobFolder != null), null);
            
            if (this._uriExternalLobFolder != null) {
              MetaColumn mcMaxLob = mdfd.getMaxLobColumn();
              if (mcMaxLob != null)
              { String sColumnName = mcMaxLob.getName();
                MetaTable mtLob = mcMaxLob.getParentMetaTable();
                String sTableName = mtLob.getName();
                MetaSchema msLob = mtLob.getParentMetaSchema();
                String sSchemaName = msLob.getName();
                
                String sMessage = "LOBs in database column \"" + sColumnName + "\" in table \"" + sTableName + "\" in schema \"" + sSchemaName + "\" will be stored externally in folder \"" + this._fileExternalLobFolder.getAbsolutePath() + "\"";
                mcMaxLob.setLobFolder(this._uriExternalLobFolder);
                if (this._sMimeType != null) {
                  mcMaxLob.setMimeType(this._sMimeType);
                  sMessage = sMessage + " with MIME type " + mcMaxLob.getMimeType();
                } 
                System.out.println();
                System.out.println(sMessage); }
              else { System.out.println(CmdI18n.get("cmd.fromDb.noLobColumnFoundToBe")); }
            
            } 
            if (this._fileExportXml != null) {
              OutputStream osXml = new FileOutputStream(this._fileExportXml);
              this._archive.exportMetaData(osXml);
              osXml.close();
            } 
            if (this._fileSiard != null)
            
            { PrimaryDataFromDb pdfd = PrimaryDataFromDb.newInstance(this._conn, this._archive);
              pdfd.setQueryTimeout(this._iQueryTimeoutSeconds);
              pdfd.download(null); }
            else { fileSiard.deleteOnExit(); }





            
            this._archive.close();
            
            this._conn.rollback();
            this._conn.close(); }
          else { System.out.println(CmdI18n.get("cmd.common.connectionToFailed", this._conn.getMetaData().getURL())); }  }
        else { System.out.println(CmdI18n.get("cmd.common.connectionToNotSupported", this._sJdbcUrl, sError)); }
         }
      else { String sMessage = "File " + this._fileSiard.getAbsolutePath();
        if (this._fileExportXml != null)
        { sMessage = sMessage + " or " + this._fileExportXml.getAbsolutePath() + " exist already!"; }
        else { sMessage = sMessage + " exists already!"; }
         System.out.println(sMessage);
        System.out.println(CmdI18n.get("cmd.fromDb.backupAndDeleteItOrUse"));
        this._iReturn = 4; }
    
    } 
  }






  
  public static void main(String[] asArgs) {
    int iReturn = 4;
    try {
      _pi.printStart();
      _pi.logStart();
      LOG.info(RuntimeHelper.getRuntimeInformation());
      SiardFromDb sfdb = new SiardFromDb(asArgs);
      _pi.logTermination();
      _pi.printTermination();
      iReturn = sfdb.getReturn();
    } catch (Exception e) {
      LOG.error("Exception while downloading SIARD archive", e);
      System.out.println(EU.getExceptionMessage(e));
      iReturn = 8;
    } catch (Error e) {
      LOG.error("Error while downloading SIARD archive", e);
      System.out.println(EU.getErrorMessage(e));
      iReturn = 12;
    } 
    System.exit(iReturn);
  }
}
