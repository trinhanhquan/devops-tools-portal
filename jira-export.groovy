import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.project.ProjectManager
import com.atlassian.jira.workflow.WorkflowManager
import com.atlassian.jira.workflow.WorkflowSchemeManager
import com.atlassian.jira.issue.issuetype.IssueTypeSchemeManager
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutManager
import com.atlassian.jira.issue.fields.layout.field.FieldLayout
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutScheme
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutSchemeEntity
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutItem
import com.atlassian.jira.issue.fields.screen.FieldScreenManager
import com.atlassian.jira.issue.fields.screen.IssueTypeScreenSchemeManager
import com.atlassian.jira.issue.fields.screen.FieldScreen
import com.atlassian.jira.issue.fields.screen.FieldScreenTab
import com.atlassian.jira.issue.fields.screen.FieldScreenLayoutItem
import com.opensymphony.workflow.loader.ActionDescriptor
import com.opensymphony.workflow.loader.FunctionDescriptor
import groovy.json.JsonOutput

// ============ CONFIG ============
def projectKey = "YOUR_PROJECT_KEY"   // <-- ĐỔI THÀNH PROJECT CỦA BẠN
// ================================

def projectManager               = ComponentAccessor.getProjectManager()       as ProjectManager
def workflowManager              = ComponentAccessor.getWorkflowManager()      as WorkflowManager
def workflowSchemeManager        = ComponentAccessor.getWorkflowSchemeManager() as WorkflowSchemeManager
def issueTypeSchemeManager       = ComponentAccessor.getIssueTypeSchemeManager() as IssueTypeSchemeManager
def fieldLayoutManager           = ComponentAccessor.getFieldLayoutManager()   as FieldLayoutManager
def fieldScreenManager           = ComponentAccessor.getFieldScreenManager()   as FieldScreenManager
def issueTypeScreenSchemeManager = ComponentAccessor.getIssueTypeScreenSchemeManager() as IssueTypeScreenSchemeManager
def fieldManager                 = ComponentAccessor.getFieldManager()

def project = projectManager.getProjectByCurrentKey(projectKey)
assert project : "❌ Project not found: ${projectKey}"

// --------------------------------------
// 1) WORKFLOW SCHEME + WORKFLOWS
// --------------------------------------
def wfScheme = workflowSchemeManager.getWorkflowScheme(project)

def result = [
    projectKey      : projectKey,
    workflowScheme  : [
        id         : wfScheme?.id,
        name       : wfScheme?.name,
        description: wfScheme?.description,
        mappings   : wfScheme?.mappings   // issueTypeId -> workflowName
    ],
    workflows       : []
]

// danh sách workflow dùng bởi project
def workflowNames = (wfScheme?.mappings?.values() ?: []) as Set

workflowNames.each { wfName ->
    def wf = workflowManager.getWorkflow(wfName)
    if (!wf) {
        log.warn("Workflow not found: ${wfName}")
        return
    }

    def descriptor = wf.getDescriptor()

    def wfData = [
        name       : wf.name,
        description: wf.description,
        isDefault  : wf.isDefault(),
        statuses   : [],
        transitions: []
    ]

    // STATUS
    wf.linkedStatusObjects.each { st ->
        wfData.statuses << [
            id            : st.id,
            name          : st.name,
            description   : st.description,
            statusCategory: st.statusCategory?.name
        ]
    }

    // TRANSITIONS + ScriptRunner post-functions
    descriptor.getAllActions().each { ActionDescriptor action ->

        def destStep     = descriptor.getStep(action.destinationStep)
        def destStatusId = destStep?.metaAttributes?.get("jira.status.id")

        def transition = [
            id           : action.id,
            name         : action.name,
            description  : action?.description,
            fromStepIds  : action.restrictTo?.stepIds ?: [],
            toStatusId   : destStatusId,
            conditions   : [],
            validators   : [],
            postFunctions: [],
            screen       : action.view?.name
        ]

        // CONDITIONS
        action.conditions?.getConditions()?.each { c ->
            transition.conditions << c.toString()
        }
        action.conditions?.getConditionalResults()?.each { cr ->
            transition.conditions << cr.toString()
        }

        // VALIDATORS
        action.validators?.each { v ->
            transition.validators << v.toString()
        }

        // POST FUNCTIONS – cố gắng detect ScriptRunner
        action.postFunctions?.each { pf ->
            if (!(pf instanceof FunctionDescriptor)) {
                transition.postFunctions << [raw: pf.toString()]
                return
            }

            FunctionDescriptor fd = (FunctionDescriptor) pf
            def args      = fd.getArgs() ?: [:]
            def className = fd.getClass()?.name
            def type      = fd.getType()
            def pluginKey = fd.getPluginKey()

            def isScriptRunner =
                    (pluginKey?.toLowerCase()?.contains("scriptrunner") ?: false) ||
                    (className?.toLowerCase()?.contains("scriptrunner") ?: false) ||
                    args.keySet().any { it.toString().toLowerCase().contains("script") }

            def scriptInfo = [:]
            if (isScriptRunner) {
                scriptInfo = [
                    inlineScript : args["script"] ?: args["INLINE_SCRIPT"] ?: args["inlineScript"],
                    scriptFile   : args["FILE"] ?: args["scriptFile"] ?: args["script.file"],
                    scriptId     : args["scriptId"] ?: args["SCRIPT_ID"],
                    extraArgs    : args
                ]
            }

            transition.postFunctions << [
                type          : type,
                className     : className,
                pluginKey     : pluginKey,
                args          : args,
                isScriptRunner: isScriptRunner,
                script        : scriptInfo
            ]
        }

        wfData.transitions << transition
    }

    result.workflows << wfData
}

// --------------------------------------
// 2) ISSUE TYPE SCHEME
// --------------------------------------
def issueTypeScheme = issueTypeSchemeManager.getIssueTypeScheme(project)

def issueTypeSchemeData = [:]
if (issueTypeScheme) {
    def issueTypes = issueTypeScheme.issueTypes ?: []
    issueTypeSchemeData = [
        id              : issueTypeScheme.id,
        name            : issueTypeScheme.name,
        description     : issueTypeScheme.description,
        defaultIssueType: issueTypeScheme.defaultIssueType?.id,
        issueTypes      : issueTypes.collect { it ->
            [
                id         : it.id,
                name       : it.name,
                description: it.description,
                type       : it.isSubTask() ? "subtask" : "standard",
                iconUrl    : it.iconUrl
            ]
        }
    ]
}
result.issueTypeScheme = issueTypeSchemeData

// --------------------------------------
// 3) FIELD CONFIGURATION SCHEME + FIELD LAYOUTS
// --------------------------------------
FieldLayoutScheme fieldLayoutScheme = fieldLayoutManager.getFieldLayoutScheme(project)

def fieldConfigData = [:]
if (fieldLayoutScheme) {

    // Entities: mỗi entity = mapping issueTypeId -> fieldLayoutId
    Collection<FieldLayoutSchemeEntity> entities =
            fieldLayoutManager.getFieldLayoutSchemeEntities(fieldLayoutScheme)

    fieldConfigData = [
        id         : fieldLayoutScheme.id,
        name       : fieldLayoutScheme.name,
        description: fieldLayoutScheme.description,
        mappings   : []
    ]

    entities?.each { FieldLayoutSchemeEntity entity ->
        String issueTypeId   = entity.issueTypeId      // null = default
        Long fieldLayoutId   = entity.fieldLayoutId
        FieldLayout fl       = fieldLayoutManager.getFieldLayout(fieldLayoutId)

        def items = fl?.fieldLayoutItems?.collect { FieldLayoutItem item ->
            def field = item.orderableField
            [
                fieldId      : field?.id,
                fieldName    : field?.name,
                isRequired   : item.isRequired(),
                isHidden     : item.isHidden(),
                description  : item.description,
                rendererType : item.rendererType
            ]
        } ?: []

        fieldConfigData.mappings << [
            issueTypeId     : issueTypeId,
            fieldLayoutId   : fl?.id,
            fieldLayoutName : fl?.name,
            fieldLayoutDescr: fl?.description,
            fields          : items
        ]
    }
}
result.fieldConfigurationScheme = fieldConfigData

// --------------------------------------
// 4) ISSUE TYPE SCREEN SCHEME + SCREEN SCHEMES + SCREENS
// --------------------------------------
def issueTypeScreenScheme = issueTypeScreenSchemeManager.getIssueTypeScreenScheme(project)

def screenData = [
    issueTypeScreenScheme: [:],
    screenSchemes        : [],
    screens              : []
]

def collectedScreenIds      = [] as Set<Long>
def collectedScreenSchemeIds = [] as Set<Long>

if (issueTypeScreenScheme) {
    screenData.issueTypeScreenScheme = [
        id         : issueTypeScreenScheme.id,
        name       : issueTypeScreenScheme.name,
        description: issueTypeScreenScheme.description
    ]

    // mỗi entity: issueTypeId -> FieldScreenScheme
    issueTypeScreenScheme.entities?.each { entity ->
        def issueTypeId = entity.issueTypeId   // null = default
        def fss         = entity.fieldScreenScheme
        if (!fss) return

        collectedScreenSchemeIds << fss.id

        def schemeItemData = [
            issueTypeId : issueTypeId,
            screenScheme: [
                id         : fss.id,
                name       : fss.name,
                description: fss.description,
                operations : []
            ]
        ]

        // operation (create/edit/view) -> FieldScreen
        fss.fieldScreenSchemeItems?.each { item ->
            def opId = item.operation?.id
            def opName =
                (opId == 1 ? "create" :
                 (opId == 2 ? "edit" :
                  (opId == 3 ? "view" : "unknown")))

            FieldScreen fs = item.fieldScreen
            if (fs) {
                collectedScreenIds << fs.id
                schemeItemData.screenScheme.operations << [
                    operationId  : opId,
                    operationName: opName,
                    screenId     : fs.id,
                    screenName   : fs.name
                ]
            }
        }

        screenData.screenSchemes << schemeItemData
    }
}

// Screens chi tiết: tabs + fields
collectedScreenIds.each { Long sid ->
    FieldScreen fs = fieldScreenManager.getFieldScreen(sid)
    if (!fs) return

    def screenItem = [
        id         : fs.id,
        name       : fs.name,
        description: fs.description,
        tabs       : []
    ]

    fs.tabs?.each { FieldScreenTab tab ->
        def tabData = [
            name  : tab.name,
            order : tab.position,
            fields: []
        ]

        tab.fieldScreenLayoutItems?.each { FieldScreenLayoutItem li ->
            def field = li.orderableField
            if (!field) return
            tabData.fields << [
                fieldId  : field.id,
                fieldName: field.name
            ]
        }

        screenItem.tabs << tabData
    }

    screenData.screens << screenItem
}
result.screensAndSchemes = screenData

// --------------------------------------
// OUTPUT JSON
// --------------------------------------
def json = JsonOutput.prettyPrint(JsonOutput.toJson(result))
return json

