SetAclAndSeriesWorkflowOperationHandler
===============================

Description
-----------

The SetAclAndSeriesWoh will add Roles to the Eipsodes, which are set from the dc-catalog.
It will also create a Series if ther is none.
The translated dc-catalog fileds are described in this table.

| DublinCore/Opencast | MCS Connect | Verwendung | Beispiel
| ------ | ------ | ------ | ------ |
| coverage | externalGroupId | Kennzeichnung einer Serie, SerienId | 3554939
| creator| userId | c-Kennung des Lehrenden, der aufnimmt (LDAP) | c102273
| title | comment | Session-Titel | Aufzeichnung 2019-12-03
| course | courseName | LV-Titel, Serientitel | 2019W171001 VO OLAT Live-Stream

Parameter Table
---------------



Operation Example
-----------------

    <operation
      id="setaclandseries"
      exception-handler-workflow="partial-error"
      description="Applying access control entries and series">
      <configurations>
      </configurations>
    </operation>
