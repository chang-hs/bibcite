import java.util.*;
import java.io.*;
import java.sql.*;
import java.util.regex.*;

/////////////////////////////////////////////////////////////////////
// Class BibManager						   //
//    This class manages the task of conversion of tex file	   //
//     with citation indicated with \cite{*} format.		   //
//    It creates the references and converts the citation	   //
//    in the appropriate format determined in the		   //
//     ornament() function.					   //
//                                                                 //
//    The default form of the references is sort by Author         //
//    The default citation format is (numbers, ..)                 //
/////////////////////////////////////////////////////////////////////

class BibManagerStroke {
    // Sorted list to put source_id in the first path	 //
    private ArrayList<paper> refSet = new ArrayList<paper>();

    // List of the source_id in Long, generated from refSet	   //
    private ArrayList<Long> refList = new ArrayList<Long>();

    //paperManager to manage the tasks related to paper //
    private paperManager manager = new paperManager();

    //Pattern for \cite{*} with minimal matching //
    private Pattern cite = Pattern.compile("(?si)\\\\cite\\{(.*?)\\}");

    private Pattern enddoc = Pattern.compile("\\\\end\\{document\\}");

    //Connection for the database "literature" //
    private Connection con = connector.getCon();

/////////////////////////////////////////////////////////////////////////////////
// Function to convert the citation markers to the appropriate		       //
// citations and append a subsection of References at the end of the text.     //
//     Parameter: text    text in String format				       //
//     Return: Converted text in String format				       //
/////////////////////////////////////////////////////////////////////////////////
    public String convert( String text ){
	Matcher matcher = cite.matcher(text);

	StringBuffer buffer = new StringBuffer();

	//First path
	// it puts all the citations found into the refSet with a key
	// of "FirstAuthor"+"source_id" and the value of the Object "paper"
	while( matcher.find( ) ) {
	    String refString = matcher.group(1);
	    String[] refNumbers = refString.split("\\s*,\\s*");
	    for(int i = 0; i < refNumbers.length; i++){
		String numberstring = refNumbers[i];
		if( numberstring.equals(""))
		    continue;
		paper p = manager.idToPaper(
				   Long.parseLong(refNumbers[i]), con);
		if(!refSet.contains((Object)p))
		    refSet.add(p);
	    }
	}

	Iterator iter = refSet.iterator();
	while(iter.hasNext()){
	    refList.add( new Long(( (paper)(iter.next()) ).getId()) );
	}


	//Second path
	// sorts the cited papers in the order of the reference number
	// and then replace it with replaceString
	// The output is written to the StringBuffer buffer.
	matcher.reset();
	while( matcher.find() ){
	    String refString = matcher.group(1);
	    String[] refNumbers = refString.split("\\s*,\\s*");
	    ArrayList<Long> refLong = new ArrayList<Long>();
	    for(int i = 0; i < refNumbers.length; i++){
		String refstring = refNumbers[i];
		if(refstring.equals(""))
		    refLong.add(new Long(0));
		else
		    refLong.add( getRefLong( new Long(
				   Long.parseLong(refNumbers[i]))));
	    }
	    Collections.sort(refLong);

	    String replaceString = "";
	    for(int i = 0; i < refNumbers.length; i++){
		replaceString += refLong.get(i).toString();
		//if not last, add the comma
		if(i != refNumbers.length - 1){
		    replaceString += ", ";
		}
	    }
	    matcher.appendReplacement(buffer, ornament(replaceString));
	}
	matcher.appendTail(buffer);


	//Generate the References section
	iter = refList.iterator();
	int i = 1;
	paper p;
	buffer.append("\n\n\\newpage\n");
	buffer.append("\\bibliographystyle{plain}\n");
	buffer.append("\\begin{thebibliography}{99}\n\n");

	String tempstring;
	while(iter.hasNext()){
	    buffer.append("\\bibitem[" + i + "]{"
			  + refList.get(i-1).toString().trim() + "}" );
	    p = manager.idToPaper(((Long)( ( iter.next() ) ))
                                                .longValue(), con);

	    if( p.getType() == 'J'){
		buffer.append(p.getAuthorString() + ": ");
		buffer.append(p.getTitle());
		tempstring = p.getTitle();
		if(tempstring.length() > 0){
		    if(tempstring.substring(tempstring.length()-1).equals( "."))
			buffer.append(" ");
		    else
			buffer.append(". ");
		}
		buffer.append(p.getJournal() + " ");
		buffer.append(p.getVolume() + ": ");
		buffer.append(p.getPages() + ", ");
		buffer.append(p.getYear());
		buffer.append("\n\n");
		i++;
	    }
	    else{
		buffer.append(p.getAuthorString() + ": ");
		buffer.append(p.getTitle());
		tempstring = p.getTitle();
		if(tempstring.length() > 0){
		    if(tempstring.substring(tempstring.length()-1).equals( "."))
			buffer.append(" ");
		    else
			buffer.append(". ");
		}
		buffer.append("In: ");
		if(!(p.getEditors().equals(""))){
		    buffer.append(p.getEditors());
		    buffer.append(" eds. ");
		}
		buffer.append(p.getBookTitle());
		tempstring = p.getBookTitle();
		if(tempstring.length() > 0){
		    if(tempstring.substring(tempstring.length()-1).equals( "."))
			buffer.append(" ");
		    else
			buffer.append(". ");
		}
		buffer.append(p.getCity() + ", ");
		buffer.append(p.getPublisher() + ", ");
		buffer.append(p.getYear() + ", ");
		buffer.append("pp" + p.getPages());
		buffer.append("\n\n");
		i++;
	    }
	}


	StringBuffer output = new StringBuffer();

	//remove the last \end{document}
	Matcher endmatcher = enddoc.matcher(buffer.toString());
	output.append(endmatcher.replaceAll(""));

	output.append("\\end{thebibliography}");
	output.append("\\end{document}");
	return output.toString();
    }
    /*
    private String getKeyString(paper p){
	ArrayList authors = p.getAuthors();
	String key = "";
	String author;
	for(int i = 0; i < authors.size(); i++){
	    key += (String)(authors.get(i));
	}
	key += p.getYear();
	key += (new Long(p.getId())).toString(); //add id number for complete sorting of the list
	return key;
    }
    */

    //Function to convert a source_id to a reference number
    private Long getRefLong(Long i){

	return new Long(refList.indexOf(i) + 1);
    }

    //Function to convert the reference numbers to the predetermined format
    //Currently only the format of superscript is supported
    private String ornament(String s){
	return "\\$^{" + s + "}\\$";

    }


    //Read the filename of args[0] and converts the citation and 
    //put the result into the standard output
    public static void main(String[] args){
	StringBuffer inbuf = new StringBuffer();
	if(args.length < 1){
	    System.out.println("Usage: java BibManager Filename");
	    System.exit(0);
	}
	File file = new File(args[0]);
	if( !file.exists() || !file.canRead()){
	    System.out.println( "Can't read " + file );
	    System.exit(0);
	}
	try{
	    FileReader fr = new FileReader( file );
	    BufferedReader in = new BufferedReader(fr);
	    String inline;
	    while((inline = in.readLine()) != null){
		inbuf.append(inline + "\n");
	    }
	}
	catch(Exception e){e.printStackTrace();}
	    
	BibManagerStroke m = new BibManagerStroke();

	System.out.println(m.convert(inbuf.toString()));
    }

}
