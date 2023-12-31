package jp.jaxa.iss.kibo.rpc.taiwan;

import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.util.ArrayList;
import java.util.List;

import jp.jaxa.iss.kibo.rpc.taiwan.helper.PathLengthHelper;
import jp.jaxa.iss.kibo.rpc.taiwan.helper.QrCodeHelper;
import jp.jaxa.iss.kibo.rpc.taiwan.obj.WayPoint;


public class YourService extends ApiWrapperService {

    private void qrCodeMission() {

        api.flashlightControlFront(0.08f);
        try {
            if(currPoint == 7) {
                Thread.sleep(2000);
            } else {
                Thread.sleep(500);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for(int i = 0; i < 3; ++i) {
            Mat mat = api.getMatNavCam();
            if(mat == null) {
                mat = api.getMatNavCam();
                if(mat == null) continue;
            }

            Mat newMat1;
            api.saveMatImage(mat, "raw.jpg");

            // undistort
            boolean undistortMode = api.getRobotKinematics().getPosition().getZ() <= 4.77;
            newMat1 = QrCodeHelper.undistortImg(mat, api.getNavCamIntrinsics(), undistortMode);

            // cut
            Rect roi;
            if(currPoint == 2) {
//                roi = new Rect(755, 0, 525, 480);
                roi = new Rect(0, 480, 640, 480);
            } else {
                roi = new Rect(0, 480, 1280, 480);
            }
            newMat1 = new Mat(newMat1, roi);
            api.saveMatImage(newMat1, "undistort" + i + ".jpg");

            // second scanning, try process
            if(i == 1) {
                newMat1 = QrCodeHelper.preprocess(newMat1);
                api.saveMatImage(newMat1, "processed.jpg");
            }

            // scan
            message = QrCodeHelper.deepScan(newMat1);

            if(!message.equals("")) break;
        }

        api.flashlightControlFront(0);

//        log("Qr code: " + message);
    }

    private boolean deactivation(List<Integer> activatedTargets) {

        // if find point 6, add qr code point
        for(int i = 0; i < activatedTargets.size(); ++i) {
            if(activatedTargets.get(i) == 6 && !isQrCodeFinished()) {
                activatedTargets.add(7);
                break;
            }
        }

        // enumerate all possible route, save best route in bestRoute, which is a WayPoint array
        decideRoute(activatedTargets);

        if(bestRouteSize > 0 && bestRoute[bestRouteSize - 1] != null && bestRoute[bestRouteSize - 1].getId() != 8) {
            WayPoint lastTarget = bestRoute[bestRouteSize - 1];
            int lastId = lastTarget.getId();
            float pathTime = lastTarget.getTime();

            pathTime += isQrCodeFinished() || lastTarget.isVisited(2) ? PathLengthHelper.getTime(lastId, 8) : PathLengthHelper.getTime(lastId, 7) + PathLengthHelper.getTime(7, 8);
            if(pathTime + 6000 > api.getTimeRemaining().get(1)) {
                return false;
            }
        }

        // movement follow the result of enumeration, if id equals 7, then perform qr code mission
        for(int i = 1; i < bestRouteSize; ++i) {
            if(bestRoute[i] != null && bestRoute[i].getId() != 0) {

                // go to goal
                if(bestRoute[i].getId() == 8) {
                    goingToGoalMission();
                    break;
                }

                // normal movement
                move(currPoint, bestRoute[i].getId());

                // at point 7 or point 2, scan qr code
                if(bestRoute[i].getId() == 7 || (bestRoute[i].getId() == 2 && !isQrCodeFinished())) {
                    qrCodeMission();
                }
            }
        }

        return true;
    }

    private void goingToGoalMission() {
        // start to go to goal
        api.notifyGoingToGoal();
        processString();

        // guess
        if(message.equals("")) {
            message = "I_AM_HERE";
        }

        // if there are running out of time, report immediately
        if(api.getTimeRemaining().get(1) < PathLengthHelper.getTime(currPoint, 8)) {
            api.reportMissionCompletion(message);
            return;
        }

        move(currPoint, 8);

        api.reportMissionCompletion(message);
    }

    @Override
    protected void runPlan1(){

        api.startMission();

        // make decision
        List<Integer> activatedTargets;
        long missionTime = api.getTimeRemaining().get(1);

        // if qr code haven't been scanned, reserve 123 sec for qr code and going to goal
        // otherwise, keep deactivating until need to go to goal (18 sec for safety)
        while(
                PathLengthHelper.getTime(currPoint, 8) + 123000 <= missionTime
                || (isQrCodeFinished() && PathLengthHelper.getTime(currPoint, 8) + 18000 <= missionTime)
        ) {
            activatedTargets = api.getActiveTargets();
            missionTime = api.getTimeRemaining().get(1);

            // find best route and go to deactivate
            if(!deactivation(activatedTargets)) {
                // have no enough time to finish route
                break;
            }

        }

        // last round
        activatedTargets = api.getActiveTargets();

        // put qr code and goal into possible choice (qr code and goal must be selected)
        if(!isQrCodeFinished()) {
            activatedTargets.add(7);
        }

        // add goal to route
        activatedTargets.add(8);

        deactivation(activatedTargets);

        // will not happen
        if(currPoint != 8) {
            goingToGoalMission();
        }
    }
}

