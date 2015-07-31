package com.mygdq.activity.model;

import java.io.IOException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.content.Context;

import com.mygdq.db.model.DBMapperAdapter;
import com.mygdq.db.model.gdq.GDQ;
import com.mygdq.db.model.gdq.GDQMapper;
import com.mygdq.db.model.run.Run;
import com.mygdq.db.model.run.RunMapper;
import com.mygdq.util.Util;

/**
 * Used to scrape the GDQ site and hold the document and list of runs statically for the app.
 * 
 * @author Michael
 */
public class GDQScraper {
	private static final String URL_SGDQ = "https://gamesdonequick.com/schedule";

	private static Document doc;
	private static Run[] runs;
	
	/**
	 * Find all the runs from the database.
	 * @param context
	 * @return
	 */
	public static Run[] getRuns(Context context) {
		if (runs == null) {
			try (DBMapperAdapter mapper = new RunMapper(context)) {
				runs = ((RunMapper) mapper).findAll();
			} catch (Exception e) { 
				/* Autocloseable requires a catch. */
			}
		}
		
		return runs;
	}
	
	/**
	 * Find the date modified from the scraped/parsed document.
	 * @return
	 * @throws ParseException
	 */
	private static Date findDateModified() throws ParseException {
		/* Get last date modified of the page */
        Elements small = doc.getElementsByClass("small");
        String date = small.get(0).text().substring(small.get(0).text().indexOf(": ") + 2).replace("am", "AM").replace("pm", "PM");;
        
        /* Create date time formatter and parse DateTime */
        final SimpleDateFormat formatter = new SimpleDateFormat("MMMM d, yyyy - h:mm a z", Locale.ENGLISH);
        
        return formatter.parse(date);
	}
	
	/**
	 * Find the list of runs from the scraped/parsed document.
	 * @return
	 * @throws ParseException
	 */
	private static Run[] findListOfRuns() throws ParseException {
		Run[] runs;
		
		/* Get run table from document (store in phone's db) */ 
        Element table = doc.getElementById("runTable");
        Elements rows = table.getElementsByTag("tr");
        
        runs = new Run[rows.size()];
        /* For each row, store list of td elements in Run object */
		for (int i = 0; i < rows.size(); ++i) {
			runs[i] = new Run(rows.get(i).getElementsByTag("td"));
		}
		
		return runs;
	}
	
	/**
	 * Check if the site has updated.
	 * @param context
	 * @return
	 * @throws ParseException
	 */
	private static boolean hasSiteUpdated(Context context) throws ParseException {
		// Find out if GDQ has updated their site.
		boolean updated = false;
		Date modified = findDateModified();
		
		try (DBMapperAdapter mapper = new GDQMapper(context)) {
			GDQ gdq = ((GDQMapper) mapper).find();
			updated = (gdq.getDate() == null || modified.getTime() > gdq.getDate().getTime());
		} catch (Exception e) { 
			/* Autocloseable requires a catch. */ 
		}
		
		return updated;
	}

	/**
	 * Scrape and parse the site as a Jsoup document.
	 * @return
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws ParseException 
	 */
	public static Document scrape(Context context) throws MalformedURLException, IOException, ParseException {
		if (doc == null) {
			doc = Jsoup.parse(Util.getHtmlFrom(URL_SGDQ));
		} else {
			if (hasSiteUpdated(context)) {
				doc = Jsoup.parse(Util.getHtmlFrom(URL_SGDQ));
			}
		}
		
		return doc;
	}
	
	/**
	 * Update the database with the list of runs.
	 * @param context
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws ParseException
	 */
	public static void updateDatabase(Context context) throws MalformedURLException, IOException, ParseException {
		// Scrape the GDQ site.
		scrape(context);
		
		// Get the list of runs from the scraper.
		runs = findListOfRuns();
		
		// Update the table.
		try (DBMapperAdapter mapper = new RunMapper(context)) {
			// Get the list of old runs.
			Run[] oldRuns = ((RunMapper) mapper).findAll();
			
			for (int i = 0; i < runs.length; ++i) {
				// If we're still in range for the old runs, update the old runs in order.
				// Otherwise, add all new / out of range runs.
				// This keep things in a certain order.
				if (i < oldRuns.length) {
					Run oldRun = new Run();
					oldRun.setRun(runs[i]);
					
					((RunMapper) mapper).update(oldRun);
				} else {
					((RunMapper) mapper).insert(runs[i]);
				}
			}
		} catch (Exception e) { 
			/* Autocloseable requires a catch. */ 
		}
	}
}