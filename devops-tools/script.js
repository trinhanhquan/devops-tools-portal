
const portalData = {
  jenkins: {
    SIT: [
      { name: "Jenkins SIT 1", url: "http://jenkins-sit-1.local" },
      { name: "Jenkins SIT 2", url: "http://jenkins-sit-2.local" }
    ],
    UAT: [
      { name: "Jenkins UAT", url: "http://jenkins-uat.local" }
    ],
    PROD: [
      { name: "Jenkins PROD", url: "http://jenkins-prod.local" }
    ]
  },
  jira: {
    SIT: [
      { name: "Jira SIT", url: "http://jira-sit.local" }
    ],
    UAT: [
      { name: "Jira UAT", url: "http://jira-uat.local" }
    ],
    PROD: [
      { name: "Jira PROD", url: "http://jira-prod.local" }
    ]
  },
  sonarqube: {
    SIT: [
      { name: "SonarQube SIT", url: "http://sonarqube-sit.local" }
    ],
    UAT: [
      { name: "SonarQube UAT", url: "http://sonarqube-uat.local" }
    ],
    PROD: [
      { name: "SonarQube PROD", url: "http://sonarqube-prod.local" }
    ]
  }
};

const icons = {
  jenkins: "fas fa-cogs",
  jira: "fab fa-atlassian",
  sonarqube: "fas fa-bug"
};

function toggleApp(appKey) {
  const container = document.getElementById("instances");
  container.innerHTML = "";
  const environments = portalData[appKey];
  if (!environments) return;

  const wrapper = document.createElement("div");
  wrapper.className = "instance-grid";
  wrapper.innerHTML = `<h3>${appKey.toUpperCase()} Environments</h3>` +
    Object.keys(environments).map(env => `
      <div class="instance-card" onclick="renderInstances('${appKey}', '${env}')">
        <i class="${icons[appKey] || 'fas fa-server'}"></i>
        <span>${env}</span>
        <button>Open ${env}</button>
      </div>
    `).join("");
  container.appendChild(wrapper);
}

function renderInstances(appKey, envKey) {
  const container = document.getElementById("instances");
  const instances = portalData[appKey][envKey] || [];
  const icon = icons[appKey] || 'fas fa-server';
  container.innerHTML = `<h3>${appKey.toUpperCase()} > ${envKey}</h3>` +
    '<div class="instance-grid">' + instances.map(i => `
      <div class="instance-card">
        <i class="${icon}"></i>
        <span>${i.name}</span>
        <a href="${i.url}" target="_blank"><button>Open</button></a>
      </div>
    `).join("") + '</div>';
}
