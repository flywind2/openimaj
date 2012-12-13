package org.openimaj.demos.sandbox.vlad;

import gnu.trove.list.array.TLongArrayList;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.Map.Entry;

import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.Text;
import org.openimaj.hadoop.sequencefile.TextBytesSequenceFileUtility;
import org.openimaj.io.IOUtils;
import org.openimaj.knn.pq.IncrementalFloatADCNearestNeighbours;
import org.openimaj.util.array.ArrayUtils;

import scala.actors.threadpool.Arrays;

public class FlickrIndexer {
	public static void convertCSV() throws IOException {
		final String CSV_REGEX = ",(?=(?:[^\"]*\"[^\"]*\")*(?![^\"]*\"))";
		final File csvFile = new File("/Volumes/Raid/FlickrCrawls/AllGeo16/images.csv");
		final File output = new File("/Users/jsh2/Desktop/flickr46m-id2lat-lng.map");

		DataOutputStream dos = null;
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(csvFile));
			dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(output)));

			String line;
			int i = 0;
			while ((line = br.readLine()) != null) {
				try {
					final String[] parts = line.split(CSV_REGEX);

					// final int farmID = Integer.parseInt(parts[0].trim());
					// final int serverID = Integer.parseInt(parts[1].trim());
					final long imageID = Long.parseLong(parts[2].trim());
					// final String secret = parts[3].trim();
					// final String url = parts[5].trim();
					final float lat = Float.parseFloat(parts[15].trim());
					final float lon = Float.parseFloat(parts[16].trim());

					dos.writeLong(imageID);
					dos.writeFloat(lat);
					dos.writeFloat(lon);
					// dos.writeInt(farmID);
					// dos.writeInt(serverID);
					// dos.writeUTF(secret);
				} catch (final Exception e) {
					// skip
				}
				if (i++ % 1000 == 0) {
					System.out.println("Read " + i + " records. " + Runtime.getRuntime().freeMemory());
				}
			}
		} finally {
			if (dos != null)
				dos.close();
			if (br != null)
				br.close();
		}
	}

	public static void extractSequenceFileData() throws IOException {
		final URI[] paths = TextBytesSequenceFileUtility
				.getFiles("hdfs://seurat/data/flickr-all-geo-vlad.seq", "part-m-");

		final File output = new File("/Volumes/My Book/flickr46m-vlad.dat");

		final DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(output)));

		final byte[] tmp = new byte[512];
		for (final URI p : paths) {
			System.out.println("Starting file " + p);

			final TextBytesSequenceFileUtility sf = new TextBytesSequenceFileUtility(p, true);

			for (final Entry<Text, BytesWritable> rec : sf) {
				final long id = Long.parseLong(rec.getKey().toString().trim());

				System.arraycopy(rec.getValue().getBytes(), 0, tmp, 0, tmp.length);

				dos.writeLong(id);
				dos.write(tmp);
			}
		}

		dos.close();
	}

	public static void createPQADCNN() throws IOException {
		final File input = new File("/Volumes/My Book/flickr46m-vlad.dat");

		final DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(input)));

		final VLADIndexer indexer = VLADIndexer.read(new File("/Users/jsh2/vlad-indexer-mirflickr25k-1x.dat"));
		final IncrementalFloatADCNearestNeighbours nn = new IncrementalFloatADCNearestNeighbours(indexer.pq, 128,
				46000000);
		final TLongArrayList indexes = new TLongArrayList(46000000);
		try {
			final float[] farr = new float[128];

			for (int x = 0;; x++) {
				if (x % 100000 == 0)
					System.out.println(x);

				final long id = dis.readLong();

				for (int i = 0; i < 128; i++) {
					farr[i] = dis.readFloat();
				}

				nn.add(farr);
				indexes.add(id);
			}
		} catch (final EOFException e) {
			dis.close();
		}

		IOUtils.writeBinary(new File("/Volumes/My Book/flickr46m-vlad-pqadcnn.dat"), nn);
		IOUtils.writeToFile(indexes, new File("/Volumes/My Book/flickr46m-vlad-pqadcnn-indexes.dat"));
	}

	public static void buildOffsets() throws IOException {
		final TLongArrayList aIds = new TLongArrayList(46000000);
		final TLongArrayList aoffsets = new TLongArrayList(46000000);

		final File input = new File("/Volumes/My Book/flickr46m-id2farm-server-secret.map");

		final DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(input)));

		try {
			int offset = 0;
			for (int i = 0;; i++) {
				if (i % 1000000 == 0)
					System.out.println(i);

				final long currentOffset = offset;
				final long id = dis.readLong();
				offset += 8;
				dis.readInt();
				offset += 4;
				dis.readInt();
				offset += 4;

				final int len = dis.readUnsignedShort();
				final byte[] tmp = new byte[len];
				dis.readFully(tmp);
				offset += len + 2;

				aIds.add(id);
				aoffsets.add(currentOffset);
			}
		} catch (final EOFException e) {
			dis.close();
		}

		final long[] ids = aIds.toArray();
		final long[] offsets = aoffsets.toArray();

		ArrayUtils.parallelQuicksortAscending(ids, offsets);

		final TLongArrayList indexedIDs = IOUtils.readFromFile(new File(
				"/Volumes/My Book/flickr46m-vlad-pqadcnn-indexes.dat"));
		final long[] indexedOffsets = new long[indexedIDs.size()];

		for (int i = 0; i < indexedOffsets.length; i++) {
			final long indexedId = indexedIDs.get(i);

			if (i % 1000000 == 0)
				System.out.println(i);

			final int idx = Arrays.binarySearch(ids, indexedId);
			indexedOffsets[i] = offsets[idx];
		}
	}

	public static void main(String[] args) throws Exception {
		// convertCSV();
		// extractSequenceFileData();
		// createPQADCNN();
		buildOffsets();
	}
}