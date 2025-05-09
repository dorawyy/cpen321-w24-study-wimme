import { Request, Response } from "express";
import User from "../schemas/UserSchema";
import Device from "../schemas/DeviceSchema";
import { removeToken } from "../utils/notificationUtils";
export class NotificationController {
  async associateDevice(req: Request, res: Response) {
    try {
      const { userId, token } = req.body;

      const user = await User.findById(userId);

      if (!user || !token) {
        return res.status(404).json({ message: "User or token not found" });
      }

      const device = await Device.findOne({ token });

      // check if device is already associated with user
      if (!device) {
        await new Device({
          userId,
          token,
        }).save();
        return res.status(200).json({ message: "Added new association" });
      } else if (device && device.userId.toString() !== userId) {
        /*
      Devices should be removed when the user logs out manually but if the user is automatically logged out by google (for some reason),
      and another user logs in, replace the user ID associated with the device to that of the new user so they don't get notifications 
      from other user
    */
        device.userId = userId;
        await device.save();
        return res
          .status(200)
          .json({ message: "Changed device association to new user" });
      }

      return res.status(200).json({ message: "Device association confirmed" });
    } catch (error) {
      console.error(error);
      res.status(500).send(error);
    }
  }

  async deleteToken(req: Request, res: Response) {
    try {
      const { token } = req.body;
      const deleted = await removeToken(token);

      if (deleted) {
        return res.status(200).json({ message: "Token deleted" });
      } else {
        return res.status(404).json({ message: "Token not found" });
      }
    } catch (error: unknown) {
      return res.status(500).json({ error: "Internal server error" });
    }
  }
}
