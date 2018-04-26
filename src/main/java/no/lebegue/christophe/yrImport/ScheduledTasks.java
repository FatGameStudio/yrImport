package no.lebegue.christophe.yrImport;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
    private static final SimpleDateFormat dateFormatXML = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static final SimpleDateFormat dateFormatDate = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat dateFormatDatetime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    String fromFile="https://www.yr.no/sted/Norge/Hedmark/Løten/Jønsrud/varsel.xml";
    String toFile="/volume1/yrVarsel/JonsrudVarsel-";
    
    @Scheduled(fixedRate = 3600000)
    public void importAndArchiveFile() 
    {	
    	 toFile = toFile + dateFormat.format(new Date())+".xml";
    	 
         try {
        	 FileUtils.copyURLToFile(new URL(fromFile), new File(toFile));
        	 log.info("File archived into " + toFile);
        	 parseFile(toFile);
         } catch (IOException e ) {
             e.printStackTrace();
         }	
         
    }
    
	public void parseFile(String file) {

		try {

			File fXmlFile = new File(file);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(fXmlFile);

			//optional, but recommended
			//read this - http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
			doc.getDocumentElement().normalize();

			
			NodeList nList = doc.getElementsByTagName("sun");
			Node node = nList.item(0);
			
			Date sunrise = dateFormatXML.parse(node.getAttributes().item(0).getNodeValue());
			Date sunset = dateFormatXML.parse(node.getAttributes().item(1).getNodeValue());
			
			String sql = "INSERT INTO sun VALUES ('"+dateFormatDate.format(sunrise)+"','"+dateFormatDatetime.format(sunrise)+"','"+dateFormatDatetime.format(sunset)+"') ON DUPLICATE KEY UPDATE sunrise='"+dateFormatDatetime.format(sunrise)+"', sunset = '"+dateFormatDatetime.format(sunset)+"'";
			log.info(sql);
			jdbcTemplate.execute(sql);
			
			nList = doc.getElementsByTagName("lastupdate");
			node = nList.item(0);
			System.out.println(node.getTextContent());
			
			
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}