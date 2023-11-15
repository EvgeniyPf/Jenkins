
//Parameters
def ACTION = 'DryRun' //WORK
def JOBFULLNAME = "parentDir/project" // or empty: "" to convert all jobs (pipeline)


import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.biouno.unochoice.CascadeChoiceParameter

long startTime = System.currentTimeMillis()
def cnt = 0
def cntsaved = 0
def cnterr = 0
def nConvResult = 0
def dateBefore = new Date()-30

println "ACTION: $ACTION"
if (JOBFULLNAME.isEmpty()) {
    Jenkins.instance.getAllItems(WorkflowJob.class)
        .findAll{it instanceof ParameterizedJobMixIn.ParameterizedJob && it.getProperty(hudson.model.ParametersDefinitionProperty) }
        .each { job ->
            if(job.getLastBuild()?.getTime()?.after(dateBefore) || job.getLastBuild() == null) {
                nConvResult = convertJob(job, ACTION)
                switch(nConvResult) {
                    case -1:
                        cnterr += 1
                    break
                    case 1:
                        cntsaved += 1
                    break
                    case 0:
                        cnt += 1
                    break
                    default:
                        println "  unexpected result convertJob: $nConvResult, job: " + job.fullName
                }
            } //if dateBefore
        }
} else {
    def job = Jenkins.instance.getItemByFullName(JOBFULLNAME)
    if (job) {
        println " job: $JOBFULLNAME"
        nConvResult = convertJob(job, ACTION)
        switch(nConvResult) {
            case -1:
                cnterr += 1
            break
            case 1:
                cntsaved += 1
            break
            case 0:
                cnt += 1
            break
            default:
                println "  unexpected result convertJob: $nConvResult"
        }
    } else {
        println " job not found: $JOBFULLNAME"
    }
}
println "Scan jobs: $cnt, modified: $cntsaved, errors: $cnterr"
println "Elapsed time: " + ((System.currentTimeMillis() - startTime) / 1000d) + "s\nEstimated time: " + 2*cntsaved + "s"


Integer convertJob(WorkflowJob job, String action) {
    def parameterName = ""
    def result = 0
    def saveJob = false
    def paramProp = job?.getProperty(hudson.model.ParametersDefinitionProperty)
    def jobParams = paramProp?.getParameterDefinitions()
    List<ParameterDefinition> newParams = new ArrayList<>()
    if (job?.fullName.contains("FOLDER_FROM_REMOVE_JOB")) {return 0}
    try {
        if (jobParams) {
            for (param in jobParams) {
                if (param.class.canonicalName.equals("org.biouno.unochoice.CascadeChoiceParameter")) {
                    if (param?.filterable != null) {
                        newParams.add(param)
                    } else {
                        saveJob = true
                        parameterName = param.getName()
                        newParams.add( new CascadeChoiceParameter(param.getName(),
                                                                param.getDescription(),
                                                                param.getRandomName(),
                                                                param.getScript(),
                                                                param.getChoiceType(),
                                                                param.getReferencedParameters(),
                                                                false,
                                                                param.getFilterLength()
                                                                ) 
                                    )
                    }
                } else {
                    // add param
                    newParams.add(param)
                }
            } //edn for
            if (saveJob) {
                result = 1
                if (action == 'WORK') {
                    job.save() // job config history - before modify
                    println "  modify:\t" + job.fullName
                    job.removeProperty(paramProp);
                    job.addProperty(new ParametersDefinitionProperty(newParams));
                } else {
                    println "  DryRun - to modify:\t" + job.fullName
                }
            }
        } // job contains jobParams
    } catch (e) {
        result = -1
        println e
        println  "Job: " + job.fullName + " Parameter: $parameterName"
    }
    newParams = null
    jobParams = null
    paramProp = null
    
    return result
}
