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
package org.openimaj.demos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.openimaj.image.DisplayUtilities;
import org.openimaj.image.ImageUtilities;
import org.openimaj.image.MBFImage;
import org.openimaj.image.colour.ColourSpace;
import org.openimaj.image.colour.RGBColour;
import org.openimaj.image.processing.resize.ResizeProcessor;
import org.openimaj.math.geometry.line.Line2d;
import org.openimaj.math.geometry.point.Point2d;
import org.openimaj.math.geometry.point.Point2dImpl;
import org.openimaj.math.geometry.transforms.RadialDistortionModel;
import org.openimaj.util.pair.IndependentPair;

import Jama.Matrix;

public class RadialCorrectionDemo {
	public static void main(String args[]) throws IOException{
		MBFImage image = ImageUtilities.readMBF(RadialCorrectionDemo.class.getResourceAsStream("/org/openimaj/image/data/fisheye.jpeg"));
		image = image.process(new ResizeProcessor(400,400));
		MBFImage corrected = image.clone();
		List<IndependentPair<Point2d,Point2d>> pairs = new ArrayList<IndependentPair<Point2d,Point2d>>();
		Point2dImpl middle = new Point2dImpl(image .getWidth()/2,image .getHeight()/2);
		Point2d[] training = null;
		training = new Point2d[]{
				new Point2dImpl(15,91),
				new Point2dImpl(75,46),
				new Point2dImpl(152,2)
			};
		appendPointsToPairs(training.clone(),middle,pairs, image.getWidth(), image.getHeight());
		
		training = new Point2d[]{
				new Point2dImpl(347,18),
				new Point2dImpl(367,148),
				new Point2dImpl(358,280)
			};
		appendPointsToPairs(training.clone(),middle,pairs, image.getWidth(), image.getHeight());
		
		RadialDistortionModel model = new RadialDistortionModel(8);
		model.setMiddle(middle);
		model.estimate(pairs);
		Matrix kMatrix = model.getKMatrix();
//		double factor = 1./kMatrix.get(0, 0);
		kMatrix.set(0, 0, 1.0);
		kMatrix.set(0, 1, 0.00002);
		kMatrix.set(0, 2, 0.00002);
		kMatrix.set(0, 3, 1);
		
		
		corrected.fill(RGBColour.BLACK);
		
		for(int y = 0; y < corrected.getHeight(); y++){
			for(int x = 0; x < corrected.getWidth(); x++){
				Point2d point = new Point2dImpl(x,y);
				Point2d pred = model.predict(point);
//				System.out.print(point + "->" + pred + ", ");
//				corrected.setPixel(x, y,image.getPixelInterp(pred.getX(), pred.getY()));
				corrected.setPixel((int)pred.getX(), (int)pred.getY(),image.getPixelInterp(x, y));
			}
//			System.out.println();
		}
		
		MBFImage compare = new MBFImage(image.getWidth() + corrected.getWidth(),image.getHeight(),ColourSpace.RGB);
		compare.drawImage(image, 0, 0);
		compare.drawImage(corrected, image.getWidth(), 0);
		
		DisplayUtilities.display(compare);
	}

	private static void appendPointsToPairs(Point2d[] training,Point2dImpl middle, List<IndependentPair<Point2d, Point2d>> pairs,int width, int height) {
//		for(int i = 0 ; i < training.length; i++){
//			training[i].setX(training[i].getX() - middle.x );
//			training[i].setY(training[i].getY() - middle.y );
//		}
		
		Line2d line = new Line2d(training[0],training[training.length-1]);
		
		for(int i = 0; i < training.length ; i++){
			IndependentPair<Point2d, Point2d> pair = RadialDistortionModel.getRadialIndependantPair(line, training[i],middle);
			
//			pairs.add(new IndependentPair<Point2d,Point2d>(pair.secondObject(),pair.firstObject()));
			pairs.add(pair);
		}
	}
}
