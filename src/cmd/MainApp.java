package cmd;
/* Dragon History voice line detector for Budokai Tenkaichi 3, by ViveTheModder */
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

public class MainApp 
{
	/* generic method that removes duplicates from any array list */
	public static <E> ArrayList<E> removeDuplicates(ArrayList<E> oldAddrList)
	{
		ArrayList<E> newAddrList = new ArrayList<E>();
		for (E i: oldAddrList)
		{
			if (!newAddrList.contains(i))
				newAddrList.add(i);
		}
		return newAddrList;
	}
	/* generic method that prints the contents of an array list without overriding the toString() method*/
	public static <E> String printVoiceLines(ArrayList<E> list)
	{
		String str = "[Voice Line IDs]\n";
		for (int i=0; i<list.size(); i++)
			str += list.get(i) + "\n";
		return str;
	}
	/* method that returns a new array list containing the unused/missing voice lines based on what the GSC uses */
	public static ArrayList<Short> getUnusedVoiceLines(ArrayList<Short> oldVoiceLines)
	{
		ArrayList<Short> newVoiceLines = new ArrayList<Short>();

		for (int i=0; i<oldVoiceLines.size()-1; i++)
		{
			for (int j=oldVoiceLines.get(i)+1; j<oldVoiceLines.get(i+1); j++)
				newVoiceLines.add((short) j);
		}
		return newVoiceLines;
	}
	/* method that returns a short in little endian */
	public static short getLittleEndianShort(short data)
	{
		ByteBuffer buffer = ByteBuffer.allocate(2);
		buffer.asShortBuffer().put(data);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		return buffer.getShort();
	}
	/* method that checks if the GSC file is an actual BT3 scenario file */
	public static boolean isInvalidGSC(RandomAccessFile gsc) throws IOException
	{
		int header = gsc.readInt(); gsc.seek(8); 
		short size = getLittleEndianShort(gsc.readShort());
		
		//check if header is different from "47 53 43 46" = GSCF (Game Scenario Contents of File)
		if (header != 0x47534346)
			return true;
		//compare the GSC file size with the file size indicated by the GSCF header
		if (size+32 != gsc.length())
			return true;
		
		return false;
	}
	
	public static void main(String[] args) throws IOException 
	{
		//create random access file
		RandomAccessFile gsc = new RandomAccessFile("GSC", "r");
		//create array lists (to store addresses and voice line IDs)
		ArrayList<Short> addrList = new ArrayList<Short>();
		ArrayList<Short> voiceLines = new ArrayList<Short>();
		
		int pos=0, curr=0; short data;
		if (isInvalidGSC(gsc))
			System.exit(1);	
		
		/* traverse the file up until encountering "01 00 03 00"
		(which indicates the start of a cutscene) */
		while (curr != 0x01000300)
		{
			curr = gsc.readInt(); 
			pos++; gsc.seek(pos);
		}
		
		/* traverse the rest of the file to find voice line pointers
		(until the program reaches "47 53 44 54" = GSDT = Game Scenario DaTa) */
		while (curr != 0x47534454)
		{
			curr = gsc.readInt();
			/* check if the current int represents a voice line command 
			 * "01 02 43 06" = main voice line (played during a cutscene)
			 * "08 76 02 00" = background voice line (playing during a fight) */
			if (curr == 0x01024306 || curr == 0x08760200)
			{
				/* increment the position by 9 bytes in order to skip:
				 * the voice line command itself (4 bytes),
				 * the character ID pointer (4 bytes)
				 * the 0A byte (which indicates that it is a short) */
				pos+=9; gsc.seek(pos);
				data = gsc.readShort();
								
				//multiply voice line pointer by 4, then add it to the address list
				addrList.add((short) (4*getLittleEndianShort(data)));
			}
			pos++; gsc.seek(pos);
		}

		addrList = removeDuplicates(addrList); //remove duplicate addresses
		addrList.sort(null); //sort addresses in ascending order
		
		//keep track of where the GSDT header starts, and then skip it
		pos+=15; gsc.seek(pos);
		int voiceCnt = 0; int gsdtStart = pos;
		
		// traverse the GSDT until EOFC (End of File Contents) is reached
		while (curr != 0x454F4643)
		{
			data = gsc.readShort();
			curr = gsc.readInt();
						
			//check if the relative position (in the GSDT itself) matches an address from the list
			if (pos-gsdtStart == addrList.get(voiceCnt))
			{
				//if so, add it to the voice line list and increment the voice line counter
				voiceLines.add(getLittleEndianShort(data));
				voiceCnt++;
			}
			//break out of the loop if there is no address for a voice line
			if (voiceCnt == addrList.size())
				break;
			
			pos+=4; gsc.seek(pos);
		} 
		
		//create new text files, and write to them after printing the lists on console
		File output1 = new File("voices-main.txt");
		File output2 = new File("voices-unused.txt");
		PrintWriter writer;
		
		voiceLines.sort(null); //sort voice lines in ascending order
		System.out.println("[Present Voice Lines]");
		
		for (int i=0; i<voiceLines.size(); i++)
			System.out.print(voiceLines.get(i)+" ");
		writer = new PrintWriter(output1);
		writer.write(printVoiceLines(voiceLines));
		writer.close();
		
		System.out.println("\n[Unused Voice Lines]");
		voiceLines = getUnusedVoiceLines(voiceLines);
		
		for (int i=0; i<voiceLines.size(); i++)
			System.out.print(voiceLines.get(i)+" ");
		writer = new PrintWriter(output2);
		writer.write(printVoiceLines(voiceLines));
		writer.close(); gsc.close();
	}
}