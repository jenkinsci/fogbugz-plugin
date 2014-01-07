Linking Jenkins with FogBugz using URLTrigger plugin for FogBugz
================================================================


By default, Fogbugz ships with an urltrigger plugin. This plugin does a GET request to the specified URL on a specified event.
To set up your Fogbugz urltrigger so Jenkins will build when the case is assigned to 'Mergekeepers', follow these instructions.
This assumes the trigger account is called 'Mergekeepers'. Of course you can customize this behaviour.


- Ensure your Jenkins instance has all Fogbugz settings filled in.
- Login as admin to your Fogbugz instance .
- In the top-right menu, click `admin` and then `plugins`.
- Click the edit icon (white paper with yellow pencil) right of `URLTrigger`.
- Add a new trigger to the following specifications:
  - React on event type `CaseAssigned` only, as this hook will trigger a build.
  - `URL` is `<url_to_jenkins>/fbTrigger/?caseid={CaseNumber}`.
  - Set `filter` to `AssignedToName = "Mergekeepers"`.
  - Set a name if you want to, it's not required.
  - Click `OK` to save the trigger.
- Profit!
