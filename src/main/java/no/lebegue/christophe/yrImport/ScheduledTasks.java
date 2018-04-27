package no.lebegue.christophe.yrImport;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

@Component
public class ScheduledTasks {

	private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
	private static final SimpleDateFormat dateFormatXML = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	private static final SimpleDateFormat dateFormatDate = new SimpleDateFormat("yyyy-MM-dd");
	private static final SimpleDateFormat dateFormatDatetime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Value("${import.fromFile}")
	String fromFile;//="https://www.yr.no/sted/Norge/Hedmark/L\u00F8ten/J\u00F8nsrud/varsel.xml";

	@Value("${import.toFolder}")
	String toFolder;//="/volume1/yrVarsel/";

	@Value("${import.toFile}")
	String toFile;//="JonsrudVarsel-";
	
	@Value("${toServer.user}")
	String user;//="sotof";

	@Value("${toServer.host}")
	String host;//="192.168.1.2";

	@Value("${toServer.keyFilePath}")
	String keyFilePath;//="/home/pi/.ssh/id_rsa";

	@Scheduled(fixedRate = 3600000)
	public void scheduler() {
		importFile();
		copyFileToServer();
		searchFileToParse();
	}
	
	public void importFile() 
	{	
		if(YrImportApplication.isImportFile()) {
			String outputFile = toFolder+toFile + dateFormat.format(new Date())+".xml";
			try {
				FileUtils.copyURLToFile(new URL(fromFile), new File(outputFile));
				log.info("File imported into " + outputFile);
			} catch (IOException e ) {
				e.printStackTrace();
			}	
		}else {
			log.info("Skip import file");
		}

	}

	public void copyFileToServer() {
		if(YrImportApplication.isCopyFileToServer()) {
			
			File folder = new File(toFolder); 
			File file;
			JSch jsch=new JSch();
			Session session = null ;
			try {
				jsch.addIdentity(keyFilePath);
			} catch (JSchException e1) {
				log.error("error adding keyFilePath "+keyFilePath);
				return;
				//e.printStackTrace();
			}
			
	    	Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            
			if(folder.isDirectory()){
				List<File> files = Arrays.asList(folder.listFiles());
				Iterator<File> fileIterator = files.iterator();
				while(fileIterator.hasNext()){
					file = fileIterator.next();
					if(file.getName().startsWith(toFile)) {
						
						if (null == session) {
							try { 
						    	session = jsch.getSession(user, host, 22);
					            session.setConfig(config);
					            session.connect();	
							} catch (JSchException e) {
								log.error("error creating session with "+host);
								return;
								//e.printStackTrace();
							} 	
						}
						
						try {
							copyLocalToRemote(session, toFolder, toFolder, file.getName());
							file.renameTo(new File(toFolder+"sendt/"+file.getName()));
							log.info("File "+file.getName()+" copied to server and archived into sendt");
							
						} catch (JSchException e) {
							log.error("error copiing file to "+host);
							return;
							//e.printStackTrace();
						} catch (IOException e) {
							log.error("error copiing file to "+host);
							return;
							//e.printStackTrace();

						}					    
					}
				}
			}
			
			if (null != session) {
				session.disconnect();
			}
			
		}else{
			log.info("Skip copy file");
		}
	}

	public void searchFileToParse() {
		if(YrImportApplication.isParseFile()) {
			File folder = new File(toFolder); 
			File file;
			if(folder.isDirectory()){
				List<File> files = Arrays.asList(folder.listFiles());
				Iterator<File> fileIterator = files.iterator();
				while(fileIterator.hasNext()){
					file = fileIterator.next();
					if(file.getName().startsWith(toFile)) {
						parseFile(file);
						file.renameTo(new File(toFolder+"processed/"+file.getName()));
						log.info("File "+file.getName()+" parsed and archived into processed");
					}
				}
			}
		}else {
			log.info("Skip parse file");
		}
	}

	public void parseFile(File XMLFile) {
		try {		

			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(XMLFile);

			//optional, but recommended
			//read this - http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
			doc.getDocumentElement().normalize();


			NodeList nList = doc.getElementsByTagName("sun");
			Node node = nList.item(0);

			Date sunrise = dateFormatXML.parse(node.getAttributes().item(0).getNodeValue());
			Date sunset = dateFormatXML.parse(node.getAttributes().item(1).getNodeValue());

			String sql = "INSERT INTO sun VALUES ('"+dateFormatDate.format(sunrise)+"','"+dateFormatDatetime.format(sunrise)+"','"+dateFormatDatetime.format(sunset)+"') ON DUPLICATE KEY UPDATE sunrise='"+dateFormatDatetime.format(sunrise)+"', sunset = '"+dateFormatDatetime.format(sunset)+"'";
			jdbcTemplate.execute(sql);

			nList = doc.getElementsByTagName("lastupdate");
			node = nList.item(0);
			//System.out.println(node.getTextContent());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	 private static void copyLocalToRemote(Session session, String from, String to, String fileName) throws JSchException, IOException {
	        boolean ptimestamp = true;
	        from = from + File.separator + fileName;

	        // exec 'scp -t rfile' remotely
	        String command = "scp " + (ptimestamp ? "-p" : "") + " -t " + to;
	        Channel channel = session.openChannel("exec");
	        ((ChannelExec) channel).setCommand(command);

	        // get I/O streams for remote scp
	        OutputStream out = channel.getOutputStream();
	        InputStream in = channel.getInputStream();

	        channel.connect();

	        if (checkAck(in) != 0) {
	            System.exit(0);
	        }

	        File _lfile = new File(from);

	        if (ptimestamp) {
	            command = "T" + (_lfile.lastModified() / 1000) + " 0";
	            // The access time should be sent here,
	            // but it is not accessible with JavaAPI ;-<
	            command += (" " + (_lfile.lastModified() / 1000) + " 0\n");
	            out.write(command.getBytes());
	            out.flush();
	            if (checkAck(in) != 0) {
	                System.exit(0);
	            }
	        }

	        // send "C0644 filesize filename", where filename should not include '/'
	        long filesize = _lfile.length();
	        command = "C0644 " + filesize + " ";
	        if (from.lastIndexOf('/') > 0) {
	            command += from.substring(from.lastIndexOf('/') + 1);
	        } else {
	            command += from;
	        }

	        command += "\n";
	        out.write(command.getBytes());
	        out.flush();

	        if (checkAck(in) != 0) {
	            System.exit(0);
	        }

	        // send a content of lfile
	        FileInputStream fis = new FileInputStream(from);
	        byte[] buf = new byte[1024];
	        while (true) {
	            int len = fis.read(buf, 0, buf.length);
	            if (len <= 0) break;
	            out.write(buf, 0, len); //out.flush();
	        }

	        // send '\0'
	        buf[0] = 0;
	        out.write(buf, 0, 1);
	        out.flush();

	        if (checkAck(in) != 0) {
	            System.exit(0);
	        }
	        out.close();

	        try {
	            if (fis != null) fis.close();
	        } catch (Exception ex) {
	            System.out.println(ex);
	        }

	        channel.disconnect();
	    }

	    public static int checkAck(InputStream in) throws IOException {
	        int b = in.read();
	        // b may be 0 for success,
	        //          1 for error,
	        //          2 for fatal error,
	        //         -1
	        if (b == 0) return b;
	        if (b == -1) return b;

	        if (b == 1 || b == 2) {
	            StringBuffer sb = new StringBuffer();
	            int c;
	            do {
	                c = in.read();
	                sb.append((char) c);
	            }
	            while (c != '\n');
	            if (b == 1) { // error
	                System.out.print(sb.toString());
	            }
	            if (b == 2) { // fatal error
	                System.out.print(sb.toString());
	            }
	        }
	        return b;
	    }

}