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
