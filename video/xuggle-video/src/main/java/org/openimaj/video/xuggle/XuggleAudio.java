/**
 * Copyright (c) 2011, The University of Southampton and the individual contributors.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *   * 	Redistributions of source code must retain the above copyright notice,
 * 	this list of conditions and the following disclaimer.
 *
 *   *	Redistributions in binary form must reproduce the above copyright notice,
 * 	this list of conditions and the following disclaimer in the documentation
 * 	and/or other materials provided with the distribution.
 *
 *   *	Neither the name of the University of Southampton nor the names of its
 * 	contributors may be used to endorse or promote products derived from this
 * 	software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/**
 * 
 */
package org.openimaj.video.xuggle;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.openimaj.audio.AudioFormat;
import org.openimaj.audio.AudioStream;
import org.openimaj.audio.SampleChunk;
import org.openimaj.audio.timecode.AudioTimecode;
import org.openimaj.io.FileUtils;

import com.xuggle.mediatool.IMediaReader;
import com.xuggle.mediatool.MediaToolAdapter;
import com.xuggle.mediatool.ToolFactory;
import com.xuggle.mediatool.event.IAudioSamplesEvent;
import com.xuggle.xuggler.Global;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IError;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;

/**
 * 	A wrapper for the Xuggle audio decoding system into the OpenIMAJ
 * 	audio system.
 *
 *	@author David Dupplaw (dpd@ecs.soton.ac.uk)
 *  @created 8 Jun 2011
 *	
 */
public class XuggleAudio extends AudioStream
{
	/** The reader used to read the video */
	private IMediaReader reader = null;
	
	/** The stream index that we'll be reading from */
	private int streamIndex = -1;
	
	/** The current sample chunk - note this is reused */
	private SampleChunk currentSamples = null;

	/** Whether we've read a complete chunk */
	private boolean chunkAvailable = false;
	
	/** The timecode of the current sample chunk */
	private final AudioTimecode currentTimecode = new AudioTimecode(0);

	/** The length of the media */
	private long length = -1;

	/** The URL being read */
	private String url;

	/** Whether to loop the file */
	private final boolean loop;
	
	/**
	 *	
	 *
	 *	@author David Dupplaw (dpd@ecs.soton.ac.uk)
	 *  @created 8 Jun 2011
	 *	
	 */
	protected class ChunkGetter extends MediaToolAdapter
	{
		/**
		 *	{@inheritDoc}
		 * 	@see com.xuggle.mediatool.MediaToolAdapter#onAudioSamples(com.xuggle.mediatool.event.IAudioSamplesEvent)
		 */
		@Override
		public void onAudioSamples( final IAudioSamplesEvent event )
		{
			// Get the samples
			final IAudioSamples aSamples = event.getAudioSamples();
			final byte[] rawBytes = aSamples.getData().
				getByteArray( 0, aSamples.getSize() );
			XuggleAudio.this.currentSamples.setSamples( rawBytes );
			
			// Set the timecode of these samples
//			double timestampMillisecs = rawBytes.length/format.getNumChannels() /
//				format.getSampleRateKHz();
			final long timestampMillisecs = TimeUnit.MILLISECONDS.convert( 
					event.getTimeStamp().longValue(), event.getTimeUnit() );
			XuggleAudio.this.currentTimecode.setTimecodeInMilliseconds( timestampMillisecs );
			XuggleAudio.this.currentSamples.setStartTimecode( XuggleAudio.this.currentTimecode );
			XuggleAudio.this.currentSamples.getFormat().setNumChannels( XuggleAudio.this.getFormat().getNumChannels() );
			XuggleAudio.this.currentSamples.getFormat().setSigned( XuggleAudio.this.getFormat().isSigned() );
			XuggleAudio.this.currentSamples.getFormat().setBigEndian( XuggleAudio.this.getFormat().isBigEndian() );
			XuggleAudio.this.currentSamples.getFormat().setSampleRateKHz( XuggleAudio.this.getFormat().getSampleRateKHz() );
			XuggleAudio.this.chunkAvailable = true;
		}
	}
	
	/**
	 * 	Default constructor that takes the file to read.
	 * 
	 *  @param file The file to read.
	 */
	public XuggleAudio( final File file )
    {
		this( file.getPath() );
    }
	
	/**
	 * 	Default constructor that takes the location of a file
	 * 	to read. This can either be a filename or a URL.
	 * 
	 *  @param url The URL of the file to read
	 */
	public XuggleAudio( final String url )
	{
		this( url, false );
	}
	
	/**
	 * 	Default constructor that takes the location of a file
	 * 	to read. This can either be a filename or a URL.
	 * 
	 *  @param u The URL of the file to read
	 */
	public XuggleAudio( final URL u )
	{
		this( u.toString(), false );
	}
	
	/**
	 * 	Default constructor that takes the location of a file
	 * 	to read. This can either be a filename or a URL. The second
	 * 	parameter determines whether the file will loop indefinitely.
	 * 	If so, {@link #nextSampleChunk()} will never return null; otherwise
	 * 	this method will return null at the end of the video.
	 * 
	 *  @param u The URL of the file to read
	 *  @param loop Whether to loop indefinitely
	 */
	public XuggleAudio( final String u, final boolean loop )
	{
		this.url = u;
		
		// If the URL given refers to a JAR resource, we need
		// to do something else, as it seems Xuggle cannot read
		// files from a JAR. So, we extract the resource to a file.
		if( FileUtils.isJarResource( u ) )
		{
			System.out.println( "XuggleVideo: Resource is in a jar file: "+this.url );
			try {
				final File f = FileUtils.unpackJarFile( new URL(u) );
				System.out.println( "Extracted to file "+f );
				this.url = f.toString();
			} catch (final MalformedURLException e) {
				e.printStackTrace();
			} catch (final IOException e) {
				e.printStackTrace();
			}
		}
		
		this.loop = loop;
		
		this.create();
	}
	
	/**
	 * 	Create the Xuggler reader
	 */
	private void create()
	{	
		// Set up a new reader to read the audio file
		this.reader = ToolFactory.makeReader( this.url );
		this.reader.addListener( new ChunkGetter() );
		this.reader.setCloseOnEofOnly( !this.loop );
		
		// We need to open the reader so that we can read the container information
		this.reader.open();
		
		// Find the audio stream.
		IStream s = null;
		int i = 0;
		while( i < this.reader.getContainer().getNumStreams() )
		{
			s = this.reader.getContainer().getStream( i );
			if( s != null && 
				s.getStreamCoder().getCodecType() == ICodec.Type.CODEC_TYPE_AUDIO )
			{
				// Save the stream index so that we only get frames from
				// this stream in the FrameGetter
				this.streamIndex = i;
				break;
			}
			i++;
		}

		if( this.reader.getContainer().getDuration() == Global.NO_PTS )
				this.length = -1;
		else	this.length = (long) (s.getDuration() * 
					s.getTimeBase().getDouble() * 1000d);

		// Get the coder for the audio stream
		final IStreamCoder aAudioCoder = this.reader.getContainer().
			getStream( this.streamIndex ).getStreamCoder();
		
		// Create an audio format object suitable for the audio
		// samples from Xuggle files
		final AudioFormat af = new AudioFormat( 
			(int)IAudioSamples.findSampleBitDepth(aAudioCoder.getSampleFormat()),
			aAudioCoder.getSampleRate()/1000d,
			aAudioCoder.getChannels() );
		af.setSigned( true );
		af.setBigEndian( false );
		super.format = af;

		System.out.println( "Using audio format: "+af );
		
		this.currentSamples = new SampleChunk( af.clone() );
    }
	
	/**
	 *	{@inheritDoc}
	 * 	@see org.openimaj.audio.AudioStream#nextSampleChunk()
	 */
	@Override
	public SampleChunk nextSampleChunk()
	{
		try
		{
			IError e = null;
			while( (e = this.reader.readPacket()) == null && !this.chunkAvailable );
			
			if( !this.chunkAvailable || e != null )
			{
				this.reader.close();
				this.reader = null;
				System.err.println( "Got audio demux error "+e.getDescription() );
				return null;
			}
			
			this.chunkAvailable  = false;
			return this.currentSamples;
		}
		catch( final Exception e )
		{
		}
		
		return null;
	}
	
	/**
	 *	{@inheritDoc}
	 * 	@see org.openimaj.audio.AudioStream#reset()
	 */
	@Override
	public void reset()
	{
		if( this.reader == null || this.reader.getContainer() == null ) 
			this.create();
		
		this.reader.getContainer().seekKeyFrame( this.streamIndex, 0, 0 );
	}

	/**
	 *	{@inheritDoc}
	 * 	@see org.openimaj.audio.AudioStream#getLength()
	 */
	@Override
	public long getLength()
	{
		return this.length ;
	}
	
	/**
	 *	{@inheritDoc}
	 * 	@see org.openimaj.audio.AudioStream#seek(long)
	 */
	@Override
	public void seek( final long timestamp )
	{
		if( this.reader == null || this.reader.getContainer() == null ) 
			this.create();

		final int i = this.reader.getContainer().seekKeyFrame( this.streamIndex, timestamp, 
				timestamp, timestamp, IContainer.SEEK_FLAG_FRAME );
		
		if( i < 0 )
			System.err.println( "Audio seek error: "+IError.errorNumberToType( i ) );
		
		this.nextSampleChunk();
	}
}
