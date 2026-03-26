import { ApiOutlined } from '@ant-design/icons';
import javaLogo from '../assets/logos/java.svg';
import nodeLogo from '../assets/logos/node.svg';
import reactLogo from '../assets/logos/react.svg';
import springBootLogo from '../assets/logos/springboot.svg';
import vueLogo from '../assets/logos/vue.svg';

/**
 * 模板类型与图标资源的映射关系。
 * 图标只是视觉展示，真正的业务语义仍然是模板类型。
 */
const iconMap = {
  react: reactLogo,
  vue: vueLogo,
  java: javaLogo,
  springboot: springBootLogo,
  springboot_frontend: springBootLogo,
  node: nodeLogo,
};

/**
 * 模板类型下拉选项。
 */
export const templateTypeOptions = [
  { label: '通用', value: 'generic' },
  { label: 'React', value: 'react' },
  { label: 'Vue', value: 'vue' },
  { label: 'Java', value: 'java' },
  { label: 'Spring Boot', value: 'springboot' },
  { label: 'Spring Boot + 前端', value: 'springboot_frontend' },
  { label: 'Node', value: 'node' },
];

/**
 * 根据模板类型推导本机构建阶段必须选择的运行环境。
 */
export function getRequiredEnvironmentTypes(templateType) {
  const normalized = templateType || 'generic';
  if (normalized === 'springboot') {
    return ['JAVA', 'MAVEN'];
  }
  if (normalized === 'springboot_frontend') {
    return ['NODE', 'JAVA', 'MAVEN'];
  }
  if (['react', 'vue', 'node'].includes(normalized)) {
    return ['NODE'];
  }
  if (normalized === 'java') {
    return ['JAVA'];
  }
  return [];
}

/**
 * 根据模板类型推导目标主机运行阶段必须选择的环境。
 */
export function getRequiredRuntimeEnvironmentTypes(templateType) {
  const normalized = templateType || 'generic';
  if (normalized === 'springboot' || normalized === 'springboot_frontend') {
    return ['JAVA'];
  }
  return [];
}

/**
 * 流水线或模板类型图标展示组件。
 */
export default function PipelineIcon({ type = 'generic' }) {
  const logo = iconMap[type];

  return (
    <div className={`pipeline-icon ${logo ? 'pipeline-icon--logo' : 'pipeline-icon--generic'}`}>
      {logo ? <img src={logo} alt={`${type} logo`} className="pipeline-icon-image" /> : <ApiOutlined />}
    </div>
  );
}
