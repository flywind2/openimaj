package org.openimaj.text.nlp.namedentity;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Class to remove stopwords from a list of tokens, or to check if a word is a stopword.
 * 
 * @author Laurence Willmore <lgw1e10@ecs.soton.ac.uk>
 *
 */
public class StopWordStripper {
	
	public static final int ENGLISH = 0;	
	private static String en_stoplistpath = "src/main/java/org/openimaj/text/nlp/namedentity/resources/en_stopwords.txt";
	private ArrayList<String> stopwords;
	
	public StopWordStripper(int language){
		this.stopwords = new ArrayList<String>();
		try{			  
			  FileInputStream fstream = new FileInputStream(getPath(language));			  
			  DataInputStream in = new DataInputStream(fstream);
			  BufferedReader br = new BufferedReader(new InputStreamReader(in));
			  String strLine;
			  //Read File Line By Line
			  while ((strLine = br.readLine()) != null)   {
			  stopwords.add(strLine.trim().toLowerCase());
			  }
			  //Close the input stream
			  in.close();
			    }catch (Exception e){//Catch exception if any
			  System.err.println("Error: " + e.getMessage());
			  }
		Collections.sort(stopwords);
	}
	
	private String getPath(int lang){
		switch(lang){
		case ENGLISH: return en_stoplistpath;
		default:return null;
		}
	}
	
	public ArrayList<String> getNonStopWords(List<String> intokens){
		ArrayList<String> result = new ArrayList<String>();
		for (String string : intokens) {
			if(Collections.binarySearch(stopwords, string)>0){
				result.add(string);
			}
		}
		return result;
	}
	
	public boolean isStopWord(String check){
		if(stopwords.contains(check.toLowerCase()))return true;
		return false;
	}

}
