package jp.jaxa.iss.kibo.rpc.defaultapk;

import java.util.ArrayList;
import java.util.List;

import gov.nasa.arc.astrobee.Result;
import gov.nasa.arc.astrobee.types.Point;
import gov.nasa.arc.astrobee.types.Quaternion;
import jp.jaxa.iss.kibo.rpc.api.KiboRpcService;


public class YourService extends KiboRpcService {
    static final boolean isDebug = true;
    static final int LOOP_MAX = 5;

    /*************** move ***************/
    private void moveTo(Point p, Quaternion q) {
        int loopCounter = 0;
        Result result = null;
        do {
            result = api.moveTo(p, q, isDebug);
            ++loopCounter;
        } while(result != null && !result.hasSucceeded() && loopCounter < LOOP_MAX);
    }

    private void move(int from, int to) {
        // same point
        if(from == to) return;

        // reverse or not
        boolean isReversed = false;
        Quaternion rotation = WayPointsHelper.getTargetRotation(to);
        if(from > to) {
            int temp = from;
            from = to;
            to = temp;
            isReversed = true;
        }

        ArrayList<Point> points = WayPointsHelper.getWayPoint(from, to);
        if(isReversed) {
            for(int i = points.size() - 2; i >= 0; --i) {
                moveTo(points.get(i), rotation);
            }
        } else {
            for(int i = 1; i < points.size(); ++i) {
                moveTo(points.get(i), rotation);
            }
        }
    }
    /*************** move end ***************/

    @Override
    protected void runPlan1(){
        api.startMission();

//        int a = 7, b = 2, c = 6, d=3;
//        move(0, a);
//        api.laserControl(true);
//        api.takeTargetSnapshot(a);
//
//        move(a, b);
////        api.saveMatImage(api.getMatNavCam(), "qr.jpg");
//        api.laserControl(true);
//        api.takeTargetSnapshot(b);
//
//        move(b, c);
//        api.laserControl(true);
//        api.takeTargetSnapshot(c);
//
//        move(c, d);
//        api.laserControl(true);
//        api.takeTargetSnapshot(d);
//
//        api.reportMissionCompletion("");
        Integer temp =0;

        int loop_count = 4;

        while(true){

            List< Integer> list = api.getActiveTargets();

            for (int i=0; i< list.size(); i++){
                move(temp, list.get(i));
                api.laserControl(true);
                api.takeTargetSnapshot(list.get(i));
                temp=list.get(i);
            }


            loop_count--;

            if (api.getTimeRemaining().get(1) < 60000){
                break;
            }

        }
        api.notifyGoingToGoal();
        move(temp, 8);
        api.reportMissionCompletion(" ");
    }


}

